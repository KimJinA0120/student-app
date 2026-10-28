package com.center.app.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// "지금"을 빈으로 주입받음 → 테스트에서 고정 시각으로 바꿔 끼울 수 있음
// 서버 PC의 시간대(한국)를 그대로 사용
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
