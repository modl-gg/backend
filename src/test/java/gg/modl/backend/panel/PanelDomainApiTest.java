package gg.modl.backend.panel;

import gg.modl.backend.support.ApiClient;
import gg.modl.backend.support.JsonHelper;
import gg.modl.backend.support.StagingCredentials;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanelDomainApiTest {

    static ApiClient api;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(StagingCredentials.isAvailable(), "Staging credentials not configured");
        api = new ApiClient();
    }

    @Test
    void getDomain() throws Exception {
        var response = api.panelGet("/v1/panel/domain");
        JsonHelper.assertStatus(response, 200);
    }

    @Test
    void getDomainStatus() throws Exception {
        var response = api.panelGet("/v1/panel/domain/status/byteful.modl.top");
        JsonHelper.assertStatus(response, 200);
    }

    @Test
    void getDomainInstructions() throws Exception {
        var response = api.panelGet("/v1/panel/domain/instructions");
        JsonHelper.assertStatus(response, 200);
    }

    @Test
    void verifyDomain() throws Exception {
        var response = api.panelPost("/v1/panel/domain/verify", "{}");
        JsonHelper.assertStatus(response, 200);
    }

    @Disabled("Skipped: would add a real custom domain on staging")
    @Test
    void addDomain() throws Exception {}

    @Disabled("Skipped: would delete the domain configuration on staging")
    @Test
    void deleteDomain() throws Exception {}
}
