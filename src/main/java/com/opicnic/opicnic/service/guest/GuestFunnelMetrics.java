package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.enums.PracticeMode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// 게스트가 실제로 쓰는지 보려는 숫자만 센다(누구인지는 남기지 않는다). 게스트 기록은 7일 뒤 지워져 DB로는 지난 이용을 셀 수 없다.
//   opicnic.guest.created                      게스트 생성
//   opicnic.guest.submitted{first, mode}       채점 제출. first=true는 그 게스트의 첫 제출(= 둘러보기를 넘어 실제로 녹음해 본 사람)
// 카운터는 재시작(배포)마다 0부터 다시 센다 — Grafana에서는 increase()로 본다.
@Component
public class GuestFunnelMetrics {

    private final MeterRegistry registry;

    public GuestFunnelMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void created() {
        registry.counter("opicnic.guest.created").increment();
    }

    public void submitted(PracticeMode mode, boolean first) {
        registry.counter("opicnic.guest.submitted", "first", String.valueOf(first), "mode", mode.name()).increment();
    }
}
