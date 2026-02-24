package gg.modl.backend.public_api;

import gg.modl.backend.support.ApiClient;
import gg.modl.backend.support.StagingCredentials;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublicStaffApiTest {

    static ApiClient api;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(StagingCredentials.isAvailable(), "Staging credentials not configured");
        api = new ApiClient();
    }

    @Test
    void acceptInvitationWithInvalidToken() throws Exception {
        // Read-only test with an invalid token to verify route returns expected error
        var response = api.publicGet("/v1/public/staff/invitations/accept?token=invalid-token");
        int status = response.statusCode();
        assertTrue(status == 400 || status == 404, "Expected 400 or 404 but got " + status);
    }

    @Test
    void acceptInvitationWithInvalidTokenPost() throws Exception {
        // Read-only test with an invalid token to verify POST route returns expected error
        var response = api.publicPost("/v1/public/staff/invitations/accept", Map.of("token", "invalid-token"));
        int status = response.statusCode();
        assertTrue(status == 400 || status == 404, "Expected 400 or 404 but got " + status);
    }
}
