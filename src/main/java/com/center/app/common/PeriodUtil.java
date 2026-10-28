package com.center.app.common;

import java.time.LocalTime;

// 교시(1~4) 시간표와 시각 → 교시 판정
public final class PeriodUtil {

	public static final int FIRST = 1;
	public static final int LAST = 4;

	// 인덱스 0은 비워 두고 교시 번호를 그대로 인덱스로 사용
	private static final LocalTime[] START = {
		null, LocalTime.of(9, 30), LocalTime.of(13, 0), LocalTime.of(15, 30), LocalTime.of(19, 0)
	};
	private static final LocalTime[] END = {
		null, LocalTime.of(12, 0), LocalTime.of(15, 30), LocalTime.of(18, 0), LocalTime.of(21, 30)
	};

	private PeriodUtil() {
	}

	public static LocalTime start(int period) {
		return START[validate(period)];
	}

	public static LocalTime end(int period) {
		return END[validate(period)];
	}

	// 쉬는 시간(12:00~13:00, 18:00~19:00)은 다음 교시, 09:30 이전은 1교시, 21:30 이후는 4교시
	public static int of(LocalTime time) {
		for (int p = FIRST; p < LAST; p++) {
			if (time.isBefore(END[p])) {
				return p;
			}
		}
		return LAST;
	}

	public static boolean isValid(int period) {
		return period >= FIRST && period <= LAST;
	}

	private static int validate(int period) {
		if (!isValid(period)) {
			throw new IllegalArgumentException("교시는 1~4만 가능합니다: " + period);
		}
		return period;
	}
}
