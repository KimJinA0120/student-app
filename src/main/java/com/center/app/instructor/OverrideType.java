package com.center.app.instructor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 날짜별 예외 종류
@Getter
@RequiredArgsConstructor
public enum OverrideType {
	ABSENT("결근"),
	CONDITIONAL("조건부"),
	EXTRA("추가 배치"),
	NOTE("메모");

	private final String label;
}
