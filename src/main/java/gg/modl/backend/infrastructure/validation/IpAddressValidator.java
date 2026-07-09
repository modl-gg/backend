package gg.modl.backend.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.Inet6Address;
import java.net.InetAddress;

public class IpAddressValidator implements ConstraintValidator<ValidIpAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return isValidIpLiteral(trimmed);
    }

    private boolean isValidIpLiteral(String s) {
        if (!s.contains(":") && s.contains(".")) {
            return isIpv4(s);
        }
        if (s.contains(":")) {
            return isIpv6(s);
        }
        return false;
    }

    private boolean isIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String octet : parts) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) {
                    return false;
                }
            }
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            int v = Integer.parseInt(octet);
            if (v < 0 || v > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6(String s) {
        int zoneIdSeparatorIndex = s.indexOf('%');
        if (zoneIdSeparatorIndex >= 0) {
            s = s.substring(0, zoneIdSeparatorIndex);
        }
        if (!s.contains(":")) {
            return false;
        }
        if (!containsOnlyIpLiteralCharacters(s)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(s);
            return (address instanceof Inet6Address) || s.contains(".");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsOnlyIpLiteralCharacters(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == ':' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
