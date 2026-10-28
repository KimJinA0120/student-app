package com.center.app.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PeriodUtilTest {

	@ParameterizedTest(name = "{0} → {1}교시")
	@CsvSource({
		"07:00, 1", // 09:30 이전은 1교시
		"09:30, 1",
		"11:59, 1",
		"12:00, 2", // 쉬는 시간은 다음 교시
		"12:30, 2",
		"13:00, 2",
		"15:29, 2",
		"15:30, 3",
		"17:59, 3",
		"18:00, 4", // 쉬는 시간은 다음 교시
		"18:30, 4",
		"21:29, 4",
		"21:31, 4", // 21:30 이후는 4교시
		"23:59, 4"
	})
	void 시각으로_교시를_판정한다(LocalTime time, int expected) {
		assertThat(PeriodUtil.of(time)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 5})
	void 범위_밖_교시는_예외(int period) {
		assertThatThrownBy(() -> PeriodUtil.start(period)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({"1, 09:30, 12:00", "2, 13:00, 15:30", "3, 15:30, 18:00", "4, 19:00, 21:30"})
	void 교시_시작_종료_시각(int period, LocalTime start, LocalTime end) {
		assertThat(PeriodUtil.start(period)).isEqualTo(start);
		assertThat(PeriodUtil.end(period)).isEqualTo(end);
	}
}
