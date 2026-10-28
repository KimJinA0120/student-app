package com.center.app.common;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 테스트용 "지금" = 2026-11-02(월) 14:00 → 2교시 진행 중
// @Import(FixedClockConfig.class)로 쓰면 실제 Clock 대신 이 빈이 주입됨(@Primary)
@TestConfiguration
public class FixedClockConfig {

	public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 14, 0);

	@Bean
	@Primary
	Clock fixedClock() {
		ZoneId zone = ZoneId.systemDefault();
		return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
	}
}
