package com.ikunkk02.wishingwillow.research.web;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class UrlSafetyValidatorTest {
    @Test
    void rejectsDangerousSchemesPortsCredentialsAndDownloadsBeforeNetwork() {
        UrlSafetyValidator validator = new UrlSafetyValidator();
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(URI.create("http://example.com"), false));
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(URI.create("file:///tmp/a"), false));
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(URI.create("https://user@example.com/"), false));
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(URI.create("https://example.com:8443/"), false));
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(URI.create("https://example.com/mod.jar"), false));
        assertThrows(UrlSafetyValidator.UnsafeUrlException.class, () -> validator.validate(
                URI.create("https://example.com/wiki?access_token=secret"), false));
    }

    @Test
    void rejectsPrivateIpv4Ipv6AndMappedPrivateAddresses() throws Exception {
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("127.0.0.1")));
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("10.1.2.3")));
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("169.254.1.1")));
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("::1")));
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("fc00::1")));
        assertFalse(UrlSafetyValidator.isPublic(InetAddress.getByName("::ffff:192.168.1.2")));
        assertTrue(UrlSafetyValidator.isPublic(InetAddress.getByName("1.1.1.1")));
    }
}
