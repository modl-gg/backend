package gg.modl.backend.infrastructure.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import gg.modl.backend.infrastructure.ratelimit.RateLimitConfig;
import gg.modl.backend.infrastructure.ratelimit.RateLimitConfig.RateLimitTier;
import gg.modl.backend.infrastructure.rest.RouteGroups;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RouteGroupClassificationCharacterizationTest {

    private enum Group {
        PANEL_AREA(RouteGroups::isPanelArea),
        ADMIN_AREA(RouteGroups::isAdminArea),
        PANEL_AUTH_AREA(RouteGroups::isPanelAuthArea),
        PANEL_PREFIX(RouteGroups::isPanelPrefix),
        MC_API_PREFIX(RouteGroups::isMinecraftApiPrefix),
        MC_OR_RL_PREFIX(RouteGroups::isMinecraftOrReplayLitePrefix),
        V1_PREFIX(RouteGroups::isVersion1Prefix),
        V2_MC_PREFIX(RouteGroups::isVersion2MinecraftPrefix),
        V3_PREFIX(RouteGroups::isVersion3Prefix),
        PANEL_CHILD(RouteGroups::isPanelChild),
        PUBLIC_CHILD(RouteGroups::isPublicChild),
        PUBLIC_RL_CHILD(RouteGroups::isPublicReplayLiteChild),
        RL_CHILD(RouteGroups::isReplayLiteChild),
        ADMIN_CHILD(RouteGroups::isAdminChild),
        ADMIN_AUTH_CHILD(RouteGroups::isAdminAuthChild);

        private final Predicate<String> predicate;

        Group(Predicate<String> predicate) {
            this.predicate = predicate;
        }

        boolean matches(String path) {
            return predicate.test(path);
        }
    }

    private static final Map<String, EnumSet<Group>> MATRIX = buildMatrix();

    private static Map<String, EnumSet<Group>> buildMatrix() {
        Map<String, EnumSet<Group>> matrix = new LinkedHashMap<>();
        put(matrix, "/v1", Group.V1_PREFIX);
        put(matrix, "/v1/", Group.V1_PREFIX);
        put(matrix, "/v1/panel", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/panel/", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panelfoo", Group.PANEL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/panel/auth", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.PANEL_AUTH_AREA, Group.V1_PREFIX);
        put(matrix, "/v1/panel/auth/", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.PANEL_AUTH_AREA, Group.V1_PREFIX);
        put(matrix, "/v1/panel/auth/send-email-code", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.PANEL_AUTH_AREA, Group.V1_PREFIX);
        put(matrix, "/v1/panel/auth/email/send-code", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.PANEL_AUTH_AREA, Group.V1_PREFIX);
        put(matrix, "/v1/panel/authx", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/authentication", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/players/uuid-1", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/players/uuid-1/punishments", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/settings/ai-apply-punishment", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/settings/punishment-types", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/appeals/appeal-1/replies", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/dashboard/alerts", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/tickets/bulk", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/panel/migration/status", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin", Group.ADMIN_AREA, Group.V1_PREFIX);
        put(matrix, "/v1/admin/", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/adminfoo", Group.V1_PREFIX);
        put(matrix, "/v1/admin/servers", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/auth", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/auth/", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.ADMIN_AUTH_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/auth/session", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.ADMIN_AUTH_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/authentication", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/beta-testers", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/admin/monitoring", Group.ADMIN_AREA, Group.ADMIN_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public", Group.V1_PREFIX);
        put(matrix, "/v1/public/", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/publicfoo", Group.V1_PREFIX);
        put(matrix, "/v1/public/tickets", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/media/presign", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/appeals", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/replay-lite", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/replay-lite/", Group.PUBLIC_CHILD, Group.PUBLIC_RL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/replay-lite/replays/uuid", Group.PUBLIC_CHILD, Group.PUBLIC_RL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/public/replay-litex", Group.PUBLIC_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/minecraft", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/minecraft/", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/minecraftfoo", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/minecraft/players/login", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/minecraft/players/sync", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/replay-lite", Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/replay-lite/", Group.MC_OR_RL_PREFIX, Group.RL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/replay-lite/replays/upload", Group.MC_OR_RL_PREFIX, Group.RL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/replay-litex", Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v2/minecraft", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V2_MC_PREFIX);
        put(matrix, "/v2/minecraft/players/login", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V2_MC_PREFIX);
        put(matrix, "/v2/minecraftfoo", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V2_MC_PREFIX);
        put(matrix, "/v2");
        put(matrix, "/v2/other");
        put(matrix, "/v3", Group.V3_PREFIX);
        put(matrix, "/v3/", Group.V3_PREFIX);
        put(matrix, "/v3/minecraft", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V3_PREFIX);
        put(matrix, "/v3/minecraft/players/sync", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V3_PREFIX);
        put(matrix, "/v3/minecraft/players/login", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V3_PREFIX);
        put(matrix, "/v3foo", Group.V3_PREFIX);
        put(matrix, "/v1/webhooks/stripe", Group.V1_PREFIX);
        put(matrix, "/v1/realtime/ws", Group.V1_PREFIX);
        put(matrix, "/v1/health", Group.V1_PREFIX);
        put(matrix, "/actuator/health");
        put(matrix, "/error");
        put(matrix, "/");
        put(matrix, "/v10/panel", Group.V1_PREFIX);
        put(matrix, "/v1minecraft", Group.V1_PREFIX);
        put(matrix, "/V1/panel");
        put(matrix, "/v1/PANEL", Group.V1_PREFIX);
        put(matrix, "/v1/panel?tab=x", Group.PANEL_PREFIX, Group.V1_PREFIX);
        put(matrix, "/v1/panel/?tab=x", Group.PANEL_AREA, Group.PANEL_PREFIX, Group.PANEL_CHILD, Group.V1_PREFIX);
        put(matrix, "/v1/minecraft?x", Group.MC_API_PREFIX, Group.MC_OR_RL_PREFIX, Group.V1_PREFIX);
        return matrix;
    }

    private static void put(Map<String, EnumSet<Group>> matrix, String path, Group... groups) {
        EnumSet<Group> set = EnumSet.noneOf(Group.class);
        Collections.addAll(set, groups);
        matrix.put(path, set);
    }

    @Test
    void routeGroupPredicatesMatchGoldenMatrix() {
        for (Map.Entry<String, EnumSet<Group>> entry : MATRIX.entrySet()) {
            String path = entry.getKey();
            EnumSet<Group> expected = entry.getValue();
            for (Group group : Group.values()) {
                assertEquals(
                    expected.contains(group),
                    group.matches(path),
                    () -> "path=" + path + " group=" + group);
            }
        }
    }

    @Test
    void routeGroupPredicatesAreNullSafe() {
        for (Group group : Group.values()) {
            assertFalse(group.matches(null), () -> "null path should not match " + group);
        }
    }

    @Test
    void apiKeyFilterClassificationMatchesCentralGroups() {
        ApiKeyFilter filter = new ApiKeyFilter(null, null, null);
        for (String path : MATRIX.keySet()) {
            boolean expected = !RouteGroups.isMinecraftOrReplayLitePrefix(path);
            assertEquals(expected, filter.shouldNotFilter(request("GET", path)), () -> "path=" + path);
        }
    }

    @Test
    void sessionFilterClassificationMatchesCentralGroups() {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(null, null, null);
        for (String path : MATRIX.keySet()) {
            boolean expected = path.startsWith("/actuator/") || RouteGroups.isMinecraftApiPrefix(path);
            assertEquals(expected, filter.shouldNotFilter(request("POST", path)), () -> "path=" + path);
        }
    }

    @Test
    void originCsrfFilterClassificationMatchesCentralGroups() {
        OriginCsrfFilter filter = new OriginCsrfFilter(new AuthConfiguration(), null);
        for (String path : MATRIX.keySet()) {
            boolean expected = !(RouteGroups.isPanelArea(path) || RouteGroups.isAdminArea(path));
            MockHttpServletRequest request = request("POST", path);
            request.setCookies(new Cookie("MODL_SESSION", "token"));
            assertEquals(expected, filter.shouldNotFilter(request), () -> "path=" + path);
        }
    }

    @Test
    void panelPermissionFilterClassificationMatchesCentralGroups() {
        PanelPermissionFilter filter = new PanelPermissionFilter(null, null, null);
        for (String path : MATRIX.keySet()) {
            boolean expected = !RouteGroups.isPanelPrefix(path) || RouteGroups.isPanelAuthArea(path);
            assertEquals(expected, filter.shouldNotFilter(request("GET", path)), () -> "path=" + path);
        }
    }

    @Test
    void protobufErrorWriterUsesVersionThreeClassificationWhenNotNegotiated() {
        ProtobufErrorResponseWriter writer = new ProtobufErrorResponseWriter();
        for (String path : MATRIX.keySet()) {
            boolean expected = RouteGroups.isVersion3Prefix(path);
            assertEquals(expected, writer.shouldWriteProtobuf(request("GET", path)), () -> "path=" + path);
        }
    }

    @Test
    void protobufErrorWriterHonorsVersionShortCircuitsAndAcceptNegotiation() {
        ProtobufErrorResponseWriter writer = new ProtobufErrorResponseWriter();

        assertEquals(true, writer.shouldWriteProtobuf(withAccept("/v3/minecraft/players/sync", "application/json")));
        assertEquals(false, writer.shouldWriteProtobuf(withAccept("/v1/panel/players", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)));
        assertEquals(false, writer.shouldWriteProtobuf(withAccept("/v2/minecraft/players/sync", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)));
        assertEquals(true, writer.shouldWriteProtobuf(withAccept("/actuator/health", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE)));
        assertEquals(false, writer.shouldWriteProtobuf(request("GET", "/actuator/health")));
    }

    @Test
    void rateLimitTierClassificationUnchangedAtGroupBoundaries() {
        RateLimitConfig config = new RateLimitConfig(new BucketPool());

        assertEquals(RateLimitTier.PANEL_AUDIT, config.getTierForPath("/v1/panel/audit/logs", "GET"));
        assertEquals(RateLimitTier.PANEL_AUDIT, config.getTierForPath("/v1/panel/analytics/overview", "GET"));
        assertEquals(RateLimitTier.PANEL_HEAVY, config.getTierForPath("/v1/panel/staff/invite", "POST"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/v1/panel/players/uuid", "GET"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/v1/panel", "GET"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/v1/panelfoo", "GET"));
        assertEquals(RateLimitTier.PUBLIC_TICKET_CREATE, config.getTierForPath("/v1/public/tickets", "POST"));
        assertEquals(RateLimitTier.PUBLIC_MEDIA_UPLOAD, config.getTierForPath("/v1/public/media/presign", "POST"));
        assertEquals(RateLimitTier.REPLAY_LITE_LABEL, config.getTierForPath("/v1/public/replay-lite/replays/x/label", "POST"));
        assertEquals(RateLimitTier.PUBLIC_STANDARD, config.getTierForPath("/v1/public/replay-lite/replays/x", "GET"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/v1/public", "GET"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/v1/publicfoo", "GET"));
        assertEquals(RateLimitTier.MINECRAFT_LOGIN, config.getTierForPath("/v1/minecraft/players/login", "POST"));
        assertEquals(RateLimitTier.MINECRAFT_STANDARD, config.getTierForPath("/v1/minecraft/players/sync", "POST"));
        assertEquals(RateLimitTier.REPLAY_LITE_UPLOAD, config.getTierForPath("/v1/replay-lite/replays/upload", "POST"));
        assertEquals(RateLimitTier.ADMIN_STANDARD, config.getTierForPath("/v1/admin/servers", "GET"));
        assertEquals(RateLimitTier.WEBHOOK, config.getTierForPath("/v1/webhooks/stripe", "POST"));
        assertEquals(RateLimitTier.PANEL_STANDARD, config.getTierForPath("/unknown", "GET"));
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static MockHttpServletRequest withAccept(String path, String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Accept", accept);
        return request;
    }
}
