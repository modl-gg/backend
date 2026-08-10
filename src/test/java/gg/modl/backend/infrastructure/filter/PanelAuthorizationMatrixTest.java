package gg.modl.backend.infrastructure.filter;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.infrastructure.authorization.PanelAccessPolicyResolver;
import gg.modl.backend.infrastructure.authorization.PanelHandlerMappingTestSupport;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PanelAuthorizationMatrixTest {

    private static final Server SERVER = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    private static final String STAFF_EMAIL = "staff@example.com";
    private static final String SUPER_ADMIN_EMAIL = "admin@example.com";
    private static final String ROLE = "helper";
    private static final String DENY_BODY =
        "{\"success\":false,\"status\":403,\"error\":\"Insufficient permissions\",\"message\":\"Insufficient permissions\"}";

    private enum Kind { PERMISSION, PERMIT, PLAYER_READ, APPEAL_REPLY }

    private record Row(String method, String path, Kind kind, String permission) {
        static Row permission(String method, String path, String permission) {
            return new Row(method, path, Kind.PERMISSION, permission);
        }

        static Row permit(String method, String path) {
            return new Row(method, path, Kind.PERMIT, null);
        }

        static Row playerRead(String path) {
            return new Row("GET", path, Kind.PLAYER_READ, null);
        }

        static Row appealReply(String path) {
            return new Row("POST", path, Kind.APPEAL_REPLY, null);
        }
    }

    private static final List<Row> MATRIX = List.of(
        Row.permission("GET", "/v1/panel/staff", "admin.staff.manage.members"),
        Row.permission("POST", "/v1/panel/staff", "admin.staff.manage.members"),
        Row.permission("GET", "/v1/panel/roles", "admin.staff.manage.roles"),
        Row.permission("POST", "/v1/panel/roles", "admin.staff.manage.roles"),
        Row.permission("GET", "/v1/panel/dashboard/metrics", "admin.audit.view.dashboard"),
        Row.permission("GET", "/v1/panel/analytics/overview", "admin.audit.view.analytics"),
        Row.permission("GET", "/v1/panel/audit/staff-performance", "admin.audit.view.logs"),
        Row.permission("POST", "/v1/panel/audit/punishments/p1/rollback", "admin.audit.rollback"),
        Row.permission("POST", "/v1/panel/audit/staff/staff1/rollback-all", "admin.audit.rollback"),
        Row.permission("POST", "/v1/panel/audit/staff/staff1/rollback-date-range", "admin.audit.rollback"),
        Row.permission("POST", "/v1/panel/audit/punishments/bulk-pardon", "admin.audit.rollback"),
        Row.permission("POST", "/v1/panel/audit/punishments/bulk-set-expiration", "admin.audit.rollback"),
        Row.permission("GET", "/v1/panel/logs", "admin.audit.view.logs"),
        Row.permission("POST", "/v1/panel/replays/r1/label", "punishment.modify"),
        Row.permission("POST", "/v1/panel/players/uuid1/notes", "punishment.modify"),
        Row.permission("POST", "/v1/panel/players/uuid1/punishments/p1/notes", "punishment.modify"),
        Row.permission("GET", "/v1/panel/tickets", "ticket.view.all"),
        Row.permission("POST", "/v1/panel/tickets", "ticket.reply.all"),
        Row.permission("POST", "/v1/panel/tickets/bulk", "ticket.close.all"),
        Row.permission("GET", "/v1/panel/ticket-subscriptions", "ticket.view.all"),
        Row.permission("POST", "/v1/panel/ticket-subscriptions/updates/u1/read", "ticket.reply.all"),
        Row.permission("GET", "/v1/panel/appeals/a1", "ticket.view.all"),
        Row.permission("PATCH", "/v1/panel/appeals/a1/status", "appeal.modify"),
        Row.permission("GET", "/v1/panel/billing/status", "admin.settings.view.billing"),
        Row.permission("POST", "/v1/panel/billing/checkout-session", "admin.settings.modify.billing"),
        Row.permission("GET", "/v1/panel/homepage-cards", "admin.settings.view.content"),
        Row.permission("POST", "/v1/panel/homepage-cards", "admin.settings.modify.content"),
        Row.permission("GET", "/v1/panel/knowledgebase/categories", "admin.settings.view.content"),
        Row.permission("POST", "/v1/panel/knowledgebase/categories", "admin.settings.modify.content"),
        Row.permission("GET", "/v1/panel/media/config", "admin.settings.view.content"),
        Row.permission("POST", "/v1/panel/media/presign", "admin.settings.modify.content"),
        Row.permission("GET", "/v1/panel/storage/quota", "admin.settings.view.storage"),
        Row.permission("POST", "/v1/panel/storage/bulk-delete", "admin.settings.modify.storage"),
        Row.permission("GET", "/v1/panel/migration/status", "admin.settings.view.migration"),
        Row.permission("POST", "/v1/panel/migration/start", "admin.settings.modify.migration"),
        Row.permission("GET", "/v1/panel/settings/general", "admin.settings.view"),
        Row.permission("PATCH", "/v1/panel/settings/general", "admin.settings.modify"),
        Row.permission("GET", "/v1/panel/settings/status-thresholds", "admin.settings.view.punishments"),
        Row.permission("PATCH", "/v1/panel/settings/status-thresholds", "admin.settings.modify.punishments"),
        Row.permission("GET", "/v1/panel/settings/ai-moderation", "admin.settings.view.punishments"),
        Row.permission("PATCH", "/v1/panel/settings/ai-moderation", "admin.settings.modify.punishments"),
        Row.permission("POST", "/v1/panel/settings/ai-dismiss-suggestion/t1", "admin.settings.modify.punishments"),
        Row.permission("GET", "/v1/panel/settings/punishment-types", "admin.settings.view.punishments"),
        Row.permission("POST", "/v1/panel/settings/punishment-types", "admin.settings.modify.punishments"),
        Row.playerRead("/v1/panel/settings/punishment-types"),
        Row.permission("GET", "/v1/panel/settings/domain", "admin.settings.view.domain"),
        Row.permission("POST", "/v1/panel/settings/domain", "admin.settings.modify.domain"),
        Row.permission("POST", "/v1/panel/settings/api-keys/minecraft/generate", "admin.settings.modify"),
        Row.permission("GET", "/v1/panel/settings/api-keys/minecraft/exists", "admin.settings.view"),
        Row.permit("GET", "/v1/panel/dashboard/alerts"),
        Row.permit("POST", "/v1/panel/players/uuid1/punishments"),
        Row.permit("POST", "/v1/panel/settings/ai-apply-punishment/t1"),
        Row.playerRead("/v1/panel/players"),
        Row.playerRead("/v1/panel/players/uuid1"),
        Row.playerRead("/v1/panel/players/uuid1/punishments/active"),
        Row.playerRead("/v1/panel/players/punishments/search"),
        Row.appealReply("/v1/panel/appeals/a1/replies")
    );

    @Test
    void authorizationMatrixIsPreservedForEveryMappedRoute() {
        List<String> failures = new ArrayList<>();

        for (Row row : MATRIX) {
            assertGranted(row, "with-permission", staffWith(row), failures);
            if (row.kind() != Kind.PERMIT) {
                assertDenied(row, "without-permission", staffWithout(), failures);
            }
            assertGranted(row, "super-admin", superAdmin(), failures);
            assertDenied(row, "unauthenticated", unauthenticated(), failures);
        }

        assertDenied(Row.permission("GET", "/v1/panel/does-not-exist", "admin.settings.view"),
            "unmapped-panel-route", staffWith(Row.permission("GET", "/v1/panel/does-not-exist", "admin.settings.view")), failures);

        if (!failures.isEmpty()) {
            Assertions.fail("Authorization matrix mismatches:\n" + String.join("\n", failures));
        }
    }

    @Test
    void unauthenticatedDeniedResponseIsWireFrozen() {
        Outcome outcome = invoke("GET", "/v1/panel/tickets", unauthenticated());
        Assertions.assertFalse(outcome.granted());
        Assertions.assertEquals(403, outcome.status());
        Assertions.assertEquals(DENY_BODY, outcome.body());
        Assertions.assertEquals("application/json", outcome.contentType());
    }

    @Test
    void insufficientPermissionDeniedResponseIsWireFrozen() {
        Outcome outcome = invoke("PATCH", "/v1/panel/settings/general", staffWithout());
        Assertions.assertFalse(outcome.granted());
        Assertions.assertEquals(403, outcome.status());
        Assertions.assertEquals(DENY_BODY, outcome.body());
        Assertions.assertEquals("application/json", outcome.contentType());
    }

    @Test
    void wrongMethodOnMappedPathLetsPermittedStaffReachDispatcher() {
        Outcome outcome = invoke("DELETE", "/v1/panel/settings/general", mocks -> {
            authenticateStaff(mocks);
            lenient().when(mocks.permissionService().hasPermission(mocks.server(), ROLE, "admin.settings.modify")).thenReturn(true);
        });
        Assertions.assertTrue(outcome.granted());
    }

    @Test
    void wrongMethodOnMappedPathDeniesNonPermittedStaffWithFrozenBody() {
        Outcome outcome = invoke("DELETE", "/v1/panel/settings/general", staffWithout());
        Assertions.assertFalse(outcome.granted());
        Assertions.assertEquals(403, outcome.status());
        Assertions.assertEquals(DENY_BODY, outcome.body());
        Assertions.assertEquals("application/json", outcome.contentType());
    }

    @Test
    void wrongMethodOnMappedPathIsUnaffectedForSuperAdmin() {
        Outcome outcome = invoke("DELETE", "/v1/panel/settings/general", superAdmin());
        Assertions.assertTrue(outcome.granted());
    }

    @Test
    void wrongMethodOnPermitAllSiblingPathReachesDispatcherForAnyAuthenticatedStaff() {
        Outcome outcome = invoke("POST", "/v1/panel/dashboard/alerts", staffWithout());
        Assertions.assertTrue(outcome.granted());
    }

    private void assertGranted(Row row, String scenario, Consumer<Mocks> setup, List<String> failures) {
        Outcome outcome = invoke(row.method(), row.path(), setup);
        if (!outcome.granted()) {
            failures.add(describe(row, scenario, "expected GRANT but was DENY(" + outcome.status() + ")"));
        }
    }

    private void assertDenied(Row row, String scenario, Consumer<Mocks> setup, List<String> failures) {
        Outcome outcome = invoke(row.method(), row.path(), setup);
        if (outcome.granted()) {
            failures.add(describe(row, scenario, "expected DENY but was GRANT"));
        } else if (outcome.status() != 403) {
            failures.add(describe(row, scenario, "expected 403 but was " + outcome.status()));
        }
    }

    private String describe(Row row, String scenario, String detail) {
        return row.method() + " " + row.path() + " [" + scenario + "] " + detail;
    }

    private Consumer<Mocks> staffWith(Row row) {
        return mocks -> {
            authenticateStaff(mocks);
            grantPermissions(mocks, row);
        };
    }

    private Consumer<Mocks> staffWithout() {
        return this::authenticateStaff;
    }

    private Consumer<Mocks> superAdmin() {
        return mocks -> {
            setSession(mocks, SUPER_ADMIN_EMAIL);
            when(mocks.permissionService().isSuperAdmin(mocks.server(), SUPER_ADMIN_EMAIL)).thenReturn(true);
        };
    }

    private Consumer<Mocks> unauthenticated() {
        return mocks -> { };
    }

    private void authenticateStaff(Mocks mocks) {
        setSession(mocks, STAFF_EMAIL);
        Staff staff = Staff.builder().email(STAFF_EMAIL).roleId(ROLE).build();
        lenient().when(mocks.staffLookupCache().findByEmail(mocks.server(), STAFF_EMAIL)).thenReturn(Optional.of(staff));
    }

    private void grantPermissions(Mocks mocks, Row row) {
        PermissionService permissionService = mocks.permissionService();
        switch (row.kind()) {
            case PERMISSION -> lenient().when(permissionService.hasPermission(mocks.server(), ROLE, row.permission())).thenReturn(true);
            case PERMIT -> { }
            case PLAYER_READ -> lenient().when(permissionService.hasPermission(mocks.server(), ROLE, "punishment.view")).thenReturn(true);
            case APPEAL_REPLY -> lenient().when(permissionService.hasPermission(mocks.server(), ROLE, "appeal.modify")).thenReturn(true);
        }
    }

    private void setSession(Mocks mocks, String email) {
        AuthSessionData session = new AuthSessionData();
        session.setEmail(email);
        mocks.request().setAttribute(RequestAttribute.SESSION, session);
    }

    private Outcome invoke(String method, String path, Consumer<Mocks> setup) {
        PermissionService permissionService = mock(PermissionService.class);
        StaffLookupCache staffLookupCache = mock(StaffLookupCache.class);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(RequestAttribute.SERVER, SERVER);
        Mocks mocks = new Mocks(permissionService, staffLookupCache, request, SERVER);
        setup.accept(mocks);

        PanelPermissionFilter filter = newFilter(permissionService, staffLookupCache);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> passed.set(true);

        try {
            filter.doFilter(request, response, chain);
            return new Outcome(passed.get(), response.getStatus(), response.getContentAsString(), response.getContentType());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final PanelAccessPolicyResolver POLICY_RESOLVER = PanelHandlerMappingTestSupport.buildResolver();

    private PanelPermissionFilter newFilter(PermissionService permissionService, StaffLookupCache staffLookupCache) {
        return new PanelPermissionFilter(permissionService, staffLookupCache, POLICY_RESOLVER);
    }

    private record Mocks(PermissionService permissionService, StaffLookupCache staffLookupCache,
                         MockHttpServletRequest request, Server server) {
    }

    private record Outcome(boolean granted, int status, String body, String contentType) {
    }
}
