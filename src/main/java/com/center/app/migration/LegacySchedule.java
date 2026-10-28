package com.center.app.migration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.center.app.instructor.InstructorRole;

// 기존 강사 일정 HTML에서 읽어 낸 원본 데이터 (변환 전)
public record LegacySchedule(
	Map<String, String> phones,                         // 이름 → 연락처
	Map<DayOfWeek, List<List<WeeklyRow>>> weekly,       // 요일별 보기: 요일 → 교시(0~3) → 확정 행
	List<LegacyDay> days,                               // 날짜별 일정
	List<String> checkItems                             // 확인 필요 목록
) {

	// 요일별 보기의 확정 행 (조건부 행은 날짜별 데이터 cd로 처리하므로 제외)
	public record WeeklyRow(String name, InstructorRole role, String note) {
	}

	public record LegacyDay(
		LocalDate date,
		List<List<String>> confirmed,                   // 교시(0~3) → 확정 강사 이름
		List<List<Conditional>> conditional,            // 교시(0~3) → 조건부 (이름, 메모)
		List<String> notes                              // "이름 내용" 형식의 메모
	) {
	}

	public record Conditional(String name, String memo) {
	}
}
