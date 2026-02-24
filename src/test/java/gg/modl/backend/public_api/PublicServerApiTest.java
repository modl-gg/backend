package gg.modl.backend.public_api;

import gg.modl.backend.support.StagingCredentials;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PublicServerApiTest {

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(StagingCredentials.isAvailable(), "Staging credentials not configured");
    }

    @Disabled("Skipped: would create a new server registration on staging")
    @Test
    void registerServer() throws Exception {}
}
