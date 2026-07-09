package gg.modl.backend.infrastructure.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.infrastructure.exception.ValidationException;
import org.junit.jupiter.api.Test;

class SafeUrlsTest {

    @Test
    void acceptsAbsoluteHttpsUrl() {
        assertTrue(SafeUrls.isSafe("https://cdn.modl.gg/x.png"));
    }

    @Test
    void acceptsAbsoluteHttpUrl() {
        assertTrue(SafeUrls.isSafe("http://example.com"));
    }

    @Test
    void acceptsSchemeCaseInsensitively() {
        assertTrue(SafeUrls.isSafe("HTTPS://EXAMPLE.COM"));
    }

    @Test
    void acceptsRootRelativePath() {
        assertTrue(SafeUrls.isSafe("/internal-page"));
    }

    @Test
    void acceptsNull() {
        assertTrue(SafeUrls.isSafe(null));
    }

    @Test
    void acceptsEmpty() {
        assertTrue(SafeUrls.isSafe(""));
    }

    @Test
    void acceptsBlank() {
        assertTrue(SafeUrls.isSafe("   "));
    }

    @Test
    void rejectsProtocolRelativeUrl() {
        assertFalse(SafeUrls.isSafe("//evil.com/x"));
    }

    @Test
    void rejectsBackslashRelativeUrl() {
        assertFalse(SafeUrls.isSafe("/\\evil.com/x"));
    }

    @Test
    void rejectsTabBypassInRootRelativePath() {
        assertFalse(SafeUrls.isSafe("/\t/evil.com"));
    }

    @Test
    void rejectsNewlineBypassInRootRelativePath() {
        assertFalse(SafeUrls.isSafe("/\n/evil.com"));
    }

    @Test
    void rejectsCarriageReturnBypassInRootRelativePath() {
        assertFalse(SafeUrls.isSafe("/\r/evil.com"));
    }

    @Test
    void rejectsSchemelessBareHost() {
        assertFalse(SafeUrls.isSafe("example.com"));
    }

    @Test
    void rejectsJavascriptScheme() {
        assertFalse(SafeUrls.isSafe("javascript:alert(1)"));
    }

    @Test
    void rejectsJavascriptSchemeMixedCase() {
        assertFalse(SafeUrls.isSafe("JavaScript:alert(1)"));
    }

    @Test
    void rejectsDataScheme() {
        assertFalse(SafeUrls.isSafe("data:text/html,<script>"));
    }

    @Test
    void rejectsBlobScheme() {
        assertFalse(SafeUrls.isSafe("blob:https://x"));
    }

    @Test
    void rejectsFtpScheme() {
        assertFalse(SafeUrls.isSafe("ftp://host/f"));
    }

    @Test
    void rejectsMailtoScheme() {
        assertFalse(SafeUrls.isSafe("mailto:a@b.com"));
    }

    @Test
    void rejectsSchemelessRelativePath() {
        assertFalse(SafeUrls.isSafe("foo/bar"));
    }

    @Test
    void rejectsMalformedUri() {
        assertFalse(SafeUrls.isSafe("ht tp://x"));
    }

    @Test
    void requireSafeThrowsForRejectedUrl() {
        assertThrows(ValidationException.class, () -> SafeUrls.requireSafe("javascript:alert(1)", "Invalid evidence URL"));
    }

    @Test
    void requireSafeDoesNotThrowForAcceptedUrl() {
        assertDoesNotThrow(() -> SafeUrls.requireSafe("https://cdn.modl.gg/x.png", "Invalid evidence URL"));
    }

    @Test
    void requireSafeDoesNotThrowForNull() {
        assertDoesNotThrow(() -> SafeUrls.requireSafe(null, "Invalid evidence URL"));
    }
}
