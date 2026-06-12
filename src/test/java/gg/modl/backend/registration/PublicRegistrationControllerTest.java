package gg.modl.backend.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.turnstile.TurnstileService;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.AutoLoginRequest;
import gg.modl.proto.modl.v1.EmailVerificationResponse;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicRegistrationControllerTest {

    @Test
    void verifyEmailReturnsAndPersistsAutoLoginToken() {
        ServerService serverService = mock(ServerService.class);
        RegistrationService registrationService = mock(RegistrationService.class);
        PublicRegistrationController controller = new PublicRegistrationController(
            serverService,
            mock(TurnstileService.class),
            mock(SessionService.class),
            registrationService,
            mock(CookieUtil.class)
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000L);
        when(serverService.verifyEmailToken("verify-token")).thenReturn(server);
        when(registrationService.generateToken()).thenReturn("auto-login-token");
        when(registrationService.createAutoLoginTokenExpiry()).thenReturn(expiresAt);

        ResponseEntity<?> response = controller.verifyEmail("verify-token");

        assertEquals(200, response.getStatusCode().value());
        EmailVerificationResponse body = (EmailVerificationResponse) response.getBody();
        assertEquals("auto-login-token", body.getAutoLoginToken());
        verify(serverService).setAutoLoginToken(server, "auto-login-token", expiresAt);
    }

    @Test
    void autoLoginDoesNotConsumeTokenBeforeServerIsReady() {
        ServerService serverService = mock(ServerService.class);
        RegistrationService registrationService = mock(RegistrationService.class);
        PublicRegistrationController controller = new PublicRegistrationController(
            serverService,
            mock(TurnstileService.class),
            mock(SessionService.class),
            registrationService,
            mock(CookieUtil.class)
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(serverService.getServerByAutoLoginToken("auto-login-token")).thenReturn(server);
        when(registrationService.isTokenExpired(server)).thenReturn(false);
        when(registrationService.checkServerReadiness(server))
            .thenReturn(RegistrationService.ServerReadiness.PROVISIONING_INCOMPLETE);

        ResponseEntity<?> response = controller.autoLogin(
            AutoLoginRequest.newBuilder().setToken("auto-login-token").build(),
            new MockHttpServletRequest(),
            new MockHttpServletResponse()
        );

        assertEquals(400, response.getStatusCode().value());
        verify(serverService, never()).consumeAutoLoginToken("auto-login-token");
    }
}
