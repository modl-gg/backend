package gg.modl.backend.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.EmailChangeService;
import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import gg.modl.backend.staff.service.StaffProfileService;
import gg.modl.proto.modl.v1.PanelUpdateEmailWithCodeRequest;
import jakarta.servlet.http.Cookie;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PanelAuthControllerTest {

    @Test
    void logoutInvalidatesOnlyCurrentCookieSessions() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        SessionService sessionService = mock(SessionService.class);
        PanelAuthController controller = createController(authConfiguration, sessionService);
        Server server = server();
        AuthSessionData session = new AuthSessionData("session-1", "staff@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/panel/auth/logout");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, session);
        request.setCookies(
            new Cookie("MODL_SESSION", "session-1"),
            new Cookie("MODL_SESSION", "session-2")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        verify(sessionService).invalidateSession(server, "session-1");
        verify(sessionService).invalidateSession(server, "session-2");
        verify(sessionService, never()).invalidateAllSessionsForEmail(any(Server.class), eq("staff@example.com"));
    }

    @Test
    void emailChangeStillInvalidatesOldEmailSessions() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        AuthService authService = mock(AuthService.class);
        SessionService sessionService = mock(SessionService.class);
        StaffProfileService staffProfileService = mock(StaffProfileService.class);
        PermissionService permissionService = mock(PermissionService.class);
        EmailChangeService emailChangeService = new EmailChangeService(
            permissionService,
            authService,
            staffProfileService,
            mock(ServerService.class),
            mock(WebAuthnService.class),
            mock(BillingService.class),
            sessionService,
            mock(EmailService.class)
        );
        PanelAuthController controller = new PanelAuthController(
            authService,
            sessionService,
            authConfiguration,
            staffProfileService,
            mock(StaffLookupCache.class),
            permissionService,
            new CookieUtil(authConfiguration),
            emailChangeService
        );
        Server server = server();
        Staff staff = new Staff();
        staff.setEmail("new@example.com");
        AuthSessionData currentSession = new AuthSessionData("old-session", "old@example.com", new Date(), new Date(), null, null);
        AuthSessionData newSession = new AuthSessionData("new-session", "new@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/v1/panel/auth/email");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, currentSession);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(permissionService.isSuperAdmin(server, "old@example.com")).thenReturn(false);
        when(staffProfileService.isStaffEmailInUse(server, "new@example.com", "old@example.com")).thenReturn(false);
        when(authService.verifyCode(server, "new@example.com", "123456")).thenReturn(true);
        when(staffProfileService.applyStaffEmailChange(server, "old@example.com", "new@example.com")).thenReturn(Optional.of(staff));
        when(sessionService.createSession(server, "new@example.com", "127.0.0.1", null)).thenReturn(newSession);

        controller.updateEmail(request, response, PanelUpdateEmailWithCodeRequest.newBuilder()
            .setNewEmail("new@example.com")
            .setCode("123456")
            .build());

        verify(sessionService).invalidateAllSessionsForEmail(server, "old@example.com");
        verify(sessionService).createSession(server, "new@example.com", "127.0.0.1", null);
    }

    @Test
    void revokeSessionReturnsNotFoundWhenPublicIdUnknown() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        SessionService sessionService = mock(SessionService.class);
        PanelAuthController controller = createController(authConfiguration, sessionService);
        Server server = server();
        AuthSessionData currentSession = new AuthSessionData("session-1", "staff@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/v1/panel/auth/sessions/pub-unknown");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, currentSession);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionService.findSessionByPublicId(server, "staff@example.com", "pub-unknown")).thenReturn(Optional.empty());

        assertEquals(404, controller.revokeSession(request, response, "pub-unknown").getStatusCode().value());
        verify(sessionService, never()).invalidateSession(any(Server.class), anyString());
    }

    @Test
    void revokeSessionInvalidatesResolvedSessionWithoutClearingCookieForOtherDevice() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        SessionService sessionService = mock(SessionService.class);
        PanelAuthController controller = createController(authConfiguration, sessionService);
        Server server = server();
        AuthSessionData currentSession = new AuthSessionData("session-1", "staff@example.com", new Date(), new Date(), null, null);
        AuthSessionData otherDevice = new AuthSessionData("session-2", "staff@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/v1/panel/auth/sessions/pub-2");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, currentSession);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionService.findSessionByPublicId(server, "staff@example.com", "pub-2")).thenReturn(Optional.of(otherDevice));

        assertEquals(200, controller.revokeSession(request, response, "pub-2").getStatusCode().value());
        verify(sessionService).invalidateSession(server, "session-2");
        assertEquals(0, response.getCookies().length);
    }

    @Test
    void revokeCurrentSessionClearsCookie() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        SessionService sessionService = mock(SessionService.class);
        PanelAuthController controller = createController(authConfiguration, sessionService);
        Server server = server();
        AuthSessionData currentSession = new AuthSessionData("session-1", "staff@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/v1/panel/auth/sessions/pub-1");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, currentSession);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionService.findSessionByPublicId(server, "staff@example.com", "pub-1")).thenReturn(Optional.of(currentSession));

        assertEquals(200, controller.revokeSession(request, response, "pub-1").getStatusCode().value());
        verify(sessionService).invalidateSession(server, "session-1");
        assertTrue(response.getCookies().length > 0);
        for (Cookie cookie : response.getCookies()) {
            assertEquals(0, cookie.getMaxAge());
        }
    }

    @Test
    void revokeAllSessionsClearsEveryEmailSessionAndCookie() {
        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setSessionCookieName("MODL_SESSION");
        SessionService sessionService = mock(SessionService.class);
        PanelAuthController controller = createController(authConfiguration, sessionService);
        Server server = server();
        AuthSessionData currentSession = new AuthSessionData("session-1", "staff@example.com", new Date(), new Date(), null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/v1/panel/auth/sessions");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setAttribute(RequestAttribute.SESSION, currentSession);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(200, controller.revokeAllSessions(request, response).getStatusCode().value());
        verify(sessionService).invalidateAllSessionsForEmail(server, "staff@example.com");
        assertTrue(response.getCookies().length > 0);
    }

    private PanelAuthController createController(AuthConfiguration authConfiguration, SessionService sessionService) {
        return new PanelAuthController(
            mock(AuthService.class),
            sessionService,
            authConfiguration,
            mock(StaffProfileService.class),
            mock(StaffLookupCache.class),
            mock(PermissionService.class),
            new CookieUtil(authConfiguration),
            mock(EmailChangeService.class)
        );
    }

    private Server server() {
        Server server = new Server("Alpha", "alpha", "server_alpha", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server_alpha");
        return server;
    }
}
