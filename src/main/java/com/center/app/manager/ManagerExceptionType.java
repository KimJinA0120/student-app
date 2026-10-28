package com.center.app.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 매니저 날짜별 예외 종류
@Getter
@RequiredArgsConstructor
public enum ManagerExceptionType {
	DAY_OFF("휴무"),
	TIME_CHANGE("시간 변경");

	private final String label;
}
