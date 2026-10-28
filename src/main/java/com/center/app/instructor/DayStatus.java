package com.center.app.instructor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 당일 상태
@Getter
@RequiredArgsConstructor
public enum DayStatus {
	BREAK("휴식"),
	OUT("외출"),
	LEAVE_EARLY("조퇴");

	private final String label;
}
