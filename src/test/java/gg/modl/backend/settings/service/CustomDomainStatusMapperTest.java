package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.server.data.CustomDomainStatus;
import org.junit.jupiter.api.Test;

class CustomDomainStatusMapperTest {
    private final CustomDomainStatusMapper mapper = new CustomDomainStatusMapper();

    @Test
    void resolveNullResultReportsError() {
        CustomDomainStatusMapper.Resolution resolution = mapper.resolve(null);

        assertEquals("error", resolution.status());
        assertEquals("error", resolution.sslStatus());
        assertFalse(resolution.cnameConfigured());
        assertTrue(resolution.error().contains("not found"));
    }

    @Test
    void resolveActiveHostnameMarksCnameConfigured() {
        CloudflareClient.CustomHostnameResult result = hostname("active",
            new CloudflareClient.CustomHostnameResult.SslStatus("active", "http", "dv"));

        CustomDomainStatusMapper.Resolution resolution = mapper.resolve(result);

        assertEquals("active", resolution.status());
        assertEquals("active", resolution.sslStatus());
        assertTrue(resolution.cnameConfigured());
        assertNull(resolution.error());
    }

    @Test
    void resolvePendingValidationMapsToPending() {
        CloudflareClient.CustomHostnameResult result = hostname("pending_validation",
            new CloudflareClient.CustomHostnameResult.SslStatus("pending_validation", "http", "dv"));

        CustomDomainStatusMapper.Resolution resolution = mapper.resolve(result);

        assertEquals("pending", resolution.status());
        assertEquals("pending", resolution.sslStatus());
        assertFalse(resolution.cnameConfigured());
        assertNull(resolution.error());
    }

    @Test
    void resolveBlockedHostnameReportsErrorRegardlessOfCase() {
        CloudflareClient.CustomHostnameResult result = hostname("Blocked", null);

        CustomDomainStatusMapper.Resolution resolution = mapper.resolve(result);

        assertEquals("error", resolution.status());
        assertEquals("pending", resolution.sslStatus());
        assertTrue(resolution.error().contains("blocked"));
    }

    @Test
    void toEnumMapsKnownStatuses() {
        assertEquals(CustomDomainStatus.ACTIVE, mapper.toEnum("active"));
        assertEquals(CustomDomainStatus.ERROR, mapper.toEnum("error"));
        assertEquals(CustomDomainStatus.PENDING, mapper.toEnum("pending"));
        assertEquals(CustomDomainStatus.PENDING, mapper.toEnum("anything-else"));
    }

    private CloudflareClient.CustomHostnameResult hostname(String status,
                                                           CloudflareClient.CustomHostnameResult.SslStatus ssl) {
        return new CloudflareClient.CustomHostnameResult("id", "support.example.com", status, ssl, null, null);
    }
}
