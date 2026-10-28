package com.center.app.manager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 하루치 매니저 근무 (저장하지 않고 계산): 근무 = 그 요일 기본 근무 − 휴무, 시간 변경은 바뀐 시간으로
public record ManagerDay(
	LocalDate date,
	List<Working> working,       // 시작 시각순
	List<Change> changes         // 휴무·시간 변경자
) {

	public record Working(Long managerId, String name, String phone, LocalTime startTime, LocalTime endTime,
			boolean changed) {
	}

	public record Change(Long managerId, String name, ManagerExceptionType type, LocalTime startTime,
			LocalTime endTime, String memo) {
	}
}
