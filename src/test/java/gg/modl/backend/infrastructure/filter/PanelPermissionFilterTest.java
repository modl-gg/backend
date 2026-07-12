package gg.modl.backend.infrastructure.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.infrastructure.authorization.PanelAccessPolicyResolver;
import gg.modl.backend.infrastructure.authorization.PanelHandlerMappingTestSupport;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PanelPermissionFilterTest {

    private static final PanelAccessPolicyResolver POLICY_RESOLVER = PanelHandlerMappingTestSupport.buildResolver();
    private static final Server SERVER = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    private static final String STAFF_EMAIL = "staff@example.com";
    private static final String SUPER_ADMIN_EMAIL = "admin@example.com";
    private static final String DENY_BODY =
        "{\"success\":false,\"status\":403,\"error\":\"Insufficient permissions\",\"message\":\"Insufficient permissions\"}";

    @Test
    void permitPolicySkipsStaffLookup() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        when(permissionService.isSuperAdmin(SERVER, STAFF_EMAIL)).thenReturn(false);
        MockHttpServletRequest request = authenticated("GET", RESTMappingV1.PANEL_DASHBOARD + "/alerts", STAFF_EMAIL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(staffLookupCache, never()).findByEmail(SERVER, STAFF_EMAIL);
    }

    @Test
    void superAdminBypassesPolicyResolutionAndStaffLookup() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        when(permissionService.isSuperAdmin(SERVER, SUPER_ADMIN_EMAIL)).thenReturn(true);
        MockHttpServletRequest request = authenticated("POST", RESTMappingV1.PANEL_STAFF, SUPER_ADMIN_EMAIL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(staffLookupCache);
    }

    @Test
    void unauthenticatedRequestIsDeniedWithFrozenBody() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", RESTMappingV1.PANEL_TICKETS);
        request.setAttribute(RequestAttribute.SERVER, SERVER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        Assertions.assertEquals(403, response.getStatus());
        Assertions.assertEquals(DENY_BODY, response.getContentAsString());
        Assertions.assertEquals("application/json", response.getContentType());
    }

    @Test
    void unmappedPanelRouteFailsClosed() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        when(permissionService.isSuperAdmin(SERVER, STAFF_EMAIL)).thenReturn(false);
        MockHttpServletRequest request = authenticated("GET", RESTMappingV1.PREFIX_PANEL + "/does-not-exist", STAFF_EMAIL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        Assertions.assertEquals(403, response.getStatus());
    }

    @Test
    void appealReplyWriteIsGrantedByTicketReplyAll() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        when(permissionService.isSuperAdmin(SERVER, STAFF_EMAIL)).thenReturn(false);
        Staff staff = Staff.builder().email(STAFF_EMAIL).roleId("helper").build();
        when(staffLookupCache.findByEmail(SERVER, STAFF_EMAIL)).thenReturn(Optional.of(staff));
        when(permissionService.hasPermission(SERVER, "helper", "appeal.modify")).thenReturn(false);
        when(permissionService.hasPermission(SERVER, "helper", "ticket.reply.all")).thenReturn(true);
        MockHttpServletRequest request = authenticated("POST", RESTMappingV1.PANEL_APPEALS + "/appeal-1/replies", STAFF_EMAIL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void appealStatusWriteIsNotGrantedByTicketReplyAll() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        PanelPermissionFilter filter = new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
        when(permissionService.isSuperAdmin(SERVER, STAFF_EMAIL)).thenReturn(false);
        Staff staff = Staff.builder().email(STAFF_EMAIL).roleId("helper").build();
        when(staffLookupCache.findByEmail(SERVER, STAFF_EMAIL)).thenReturn(Optional.of(staff));
        when(permissionService.hasPermission(SERVER, "helper", "appeal.modify")).thenReturn(false);
        MockHttpServletRequest request = authenticated("PATCH", RESTMappingV1.PANEL_APPEALS + "/appeal-1/status", STAFF_EMAIL);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        Assertions.assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest authenticated(String method, String path, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(RequestAttribute.SERVER, SERVER);
        AuthSessionData session = new AuthSessionData();
        session.setEmail(email);
        request.setAttribute(RequestAttribute.SESSION, session);
        return request;
    }
}
