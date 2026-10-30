package com.center.app.instructor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 당일 상태. 휴식·외출은 10/29에 없앴고 조퇴만 남음
// (enum으로 둔 이유: 컬럼이 varchar라 나중에 값을 다시 늘려도 테이블을 안 바꿔도 됨)
@Getter
@RequiredArgsConstructor
public enum DayStatus {
	LEAVE_EARLY("조퇴");

	private final String label;
}
