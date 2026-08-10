package com.ikunkk02.wishingwillow.research.web;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class UrlSafetyValidator {
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".jar", ".zip", ".exe", ".dll", ".msi", ".bat", ".cmd", ".ps1", ".sh", ".dmg", ".pkg"
    );
    private static final Set<String> PLATFORM_HOSTS = Set.of(
            "api.modrinth.com", "api.curseforge.com", "api.github.com", "github.com",
            "raw.githubusercontent.com", "modrinth.com", "www.modrinth.com",
            "curseforge.com", "www.curseforge.com"
    );

    private final boolean allowLoopbackForTests;

    public UrlSafetyValidator() { this(false); }
    public UrlSafetyValidator(boolean allowLoopbackForTests) { this.allowLoopbackForTests = allowLoopbackForTests; }

    public URI validate(URI uri, boolean platformOnly) {
        boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme())
                || (allowLoopbackForTests && "http".equalsIgnoreCase(uri.getScheme()));
        if (!schemeAllowed || uri.getUserInfo() != null || uri.getHost() == null
                || (!allowLoopbackForTests && uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new UnsafeUrlException("UNSAFE_URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase(Locale.ROOT);
        if (query.matches(".*(?:^|[&])(api[_-]?key|access[_-]?token|auth|authorization|signature|x-api-key)=.*")) {
            throw new UnsafeUrlException("SENSITIVE_URL_REJECTED");
        }
        if (platformOnly && !PLATFORM_HOSTS.contains(host) && !allowLoopbackForTests) {
            throw new UnsafeUrlException("UNTRUSTED_HOST");
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (BINARY_EXTENSIONS.stream().anyMatch(path::endsWith)) {
            throw new UnsafeUrlException("DOWNLOAD_URL_REJECTED");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isAllowedAddress(host, address) && !allowLoopbackForTests) {
                    throw new UnsafeUrlException("UNSAFE_ADDRESS");
                }
            }
        } catch (IOException exception) {
            throw new UnsafeUrlException("DNS");
        }
        return uri;
    }

    public static boolean isAllowedAddress(String host, InetAddress address) {
        return isPublic(address) || (PLATFORM_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                && isProxyBenchmarkAddress(address));
    }

    public static boolean isProxyBenchmarkAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address && (bytes[0] & 0xff) == 198
                && ((bytes[1] & 0xff) == 18 || (bytes[1] & 0xff) == 19);
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 0xff, b = bytes[1] & 0xff;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127) || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31) || (a == 192 && (b == 0 || b == 168))
                    || (a == 198 && (b == 18 || b == 19)) || (a == 203 && b == 0));
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            boolean ula = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8;
            boolean mapped = first == 0 && java.util.stream.IntStream.range(0, 10).allMatch(i -> bytes[i] == 0)
                    && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
            if (mapped) {
                byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                try { return isPublic(InetAddress.getByAddress(ipv4)); } catch (IOException ignored) { return false; }
            }
            return !ula && !documentation;
        }
        return false;
    }

    public static final class UnsafeUrlException extends RuntimeException {
        public UnsafeUrlException(String code) { super(code); }
    }
}
