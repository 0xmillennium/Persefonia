package dev.persefonia.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrustedProxyCidrsTest {
    @Test
    void acceptsSupportedIpv4AndIpv6CidrLists() {
        assertThat(TrustedProxyCidrs.isSafeConfiguredList("172.20.0.0/16")).isTrue();
        assertThat(TrustedProxyCidrs.isSafeConfiguredList("10.20.0.0/16,172.30.0.0/16"))
                .isTrue();
        assertThat(TrustedProxyCidrs.isSafeConfiguredList("fd00::/64")).isTrue();
        assertThat(TrustedProxyCidrs.isSafeConfiguredList("172.20.0.0/16,fd00::/64"))
                .isTrue();
    }

    @Test
    void rejectsUnsafeMalformedAndUnsupportedCidrLists() {
        for (String value : new String[] {
            "",
            "0.0.0.0/0",
            "::/0",
            "10.0.0.0/8,0.0.0.0/0",
            "fd00::/8,::/0",
            "*",
            ".*",
            "10.0.0.1",
            "10.0.0.0/not-a-prefix",
            "10.0.0.0/33",
            "fd00::/129",
            "10.0.0.0/8,",
            ",10.0.0.0/8"
        }) {
            assertThat(TrustedProxyCidrs.isSafeConfiguredList(value)).isFalse();
        }
    }
}
