package gg.modl.backend.migration.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MigrationValidatorIpTest {

    private final MigrationValidator validator = new MigrationValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "0.0.0.0",
        "1.2.3.4",
        "192.168.1.1",
        "255.255.255.255",
        "10.0.0.255"
    })
    void acceptsValidIpv4(String ip) {
        assertTrue(validator.isValidIpAddress(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "::",
        "::1",
        "fe80::1",
        "2001:db8::1",
        "2001:0db8:0000:0000:0000:0000:0000:0001",
        "::ffff:192.168.1.1",
        "1:2:3:4:5:6:192.168.1.1"
    })
    void acceptsValidIpv6(String ip) {
        assertTrue(validator.isValidIpAddress(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "example.com",
        "localhost",
        "player.mojang.com",
        "not-an-ip"
    })
    void rejectsHostnames(String hostname) {
        assertFalse(validator.isValidIpAddress(hostname));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "192.168.01.1",
        "01.02.03.04",
        "192.168.001.1"
    })
    void rejectsLeadingZeroOctets(String ip) {
        assertFalse(validator.isValidIpAddress(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "256.1.1.1",
        "1.2.3.256",
        "999.999.999.999",
        "1.2.3",
        "1.2.3.4.5"
    })
    void rejectsOutOfRangeOrMalformedIpv4(String ip) {
        assertFalse(validator.isValidIpAddress(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1::2::3",
        ":::",
        "12345::",
        "gggg::1",
        "1:2:3:4:5:6:7:8:9"
    })
    void rejectsMalformedIpv6(String ip) {
        assertFalse(validator.isValidIpAddress(ip));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsNullEmptyAndBlank(String ip) {
        assertFalse(validator.isValidIpAddress(ip));
    }
}
