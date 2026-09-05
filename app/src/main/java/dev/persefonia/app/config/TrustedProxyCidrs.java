package dev.persefonia.app.config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Production policy for Tomcat's comma-separated trusted-proxy CIDRs. */
final class TrustedProxyCidrs {
    private static final String MISSING_CONFIGURATION_SENTINEL = "127.0.0.1/32";

    private TrustedProxyCidrs() {}

    static boolean isSafeConfiguredList(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return false;
        }

        for (String token : configuredCidrs.split(",", -1)) {
            if (!isSafeCidr(token.trim())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeCidr(String cidr) {
        if (cidr.isEmpty() || MISSING_CONFIGURATION_SENTINEL.equals(cidr)) {
            return false;
        }

        int slash = cidr.indexOf('/');
        if (slash <= 0 || slash != cidr.lastIndexOf('/')) {
            return false;
        }

        String address = cidr.substring(0, slash);
        Integer prefixLength = parseDecimal(cidr.substring(slash + 1));
        if (prefixLength == null) {
            return false;
        }

        if (address.contains(":")) {
            return isValidIpv6(address, prefixLength) && prefixLength != 0;
        }
        return isValidIpv4(address, prefixLength) && prefixLength != 0;
    }

    private static boolean isValidIpv4(String address, int prefixLength) {
        if (prefixLength > 32) {
            return false;
        }

        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            Integer value = parseDecimal(octet);
            if (value == null || value > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIpv6(String address, int prefixLength) {
        if (prefixLength > 128 || !address.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(address) instanceof Inet6Address;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static Integer parseDecimal(String value) {
        if (value.isEmpty()) {
            return null;
        }

        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return null;
            }
            if (result > 1000) {
                return null;
            }
            result = result * 10 + (character - '0');
        }
        return result;
    }
}
