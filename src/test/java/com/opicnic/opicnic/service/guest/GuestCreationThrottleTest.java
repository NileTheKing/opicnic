package com.opicnic.opicnic.service.guest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCreationThrottleTest {

    private final GuestProperties properties = new GuestProperties();   // IP당 시간 3개
    private final GuestCreationThrottle throttle = new GuestCreationThrottle(properties);

    @Test
    void 같은_IP는_시간당_한도까지만_허용하고_다른_IP는_영향받지_않는다() {
        assertThat(throttle.tryAcquire("1.1.1.1")).isTrue();
        assertThat(throttle.tryAcquire("1.1.1.1")).isTrue();
        assertThat(throttle.tryAcquire("1.1.1.1")).isTrue();
        assertThat(throttle.tryAcquire("1.1.1.1")).isFalse();
        assertThat(throttle.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void 한도는_설정으로_바뀐다() {
        properties.setIpHourlyCreations(1);
        assertThat(throttle.tryAcquire("3.3.3.3")).isTrue();
        assertThat(throttle.tryAcquire("3.3.3.3")).isFalse();
    }

    @Test
    void 클라이언트_IP는_Cloudflare_헤더를_먼저_보고_없으면_원격_주소() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        assertThat(GuestCreationThrottle.clientIp(request)).isEqualTo("10.0.0.5");

        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        assertThat(GuestCreationThrottle.clientIp(request)).isEqualTo("203.0.113.9");
    }
}
