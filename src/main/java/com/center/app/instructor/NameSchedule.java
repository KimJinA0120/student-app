package com.center.app.instructor;

import java.time.LocalDate;
import java.util.List;

// 이름 검색 결과: 한 강사의 일정을 날짜순으로 (DaySchedule에서 그 강사 부분만 뽑은 것)
public record NameSchedule(
	Long instructorId,
	String name,
	String phone,
	List<DateLine> dates  // 확정·조건부·결근·메모 중 하나라도 있는 날짜만
) {

	// 기간 전체의 확정 근무 교시 수
	public int confirmedCount() {
		return dates.stream().mapToInt(d -> d.confirmed().size()).sum();
	}

	public record DateLine(
		LocalDate date,
		List<Integer> confirmed,    // 확정 근무 교시
		List<Integer> conditional,  // 조건부 교시
		List<String> memos          // 조건부 메모, 결근, 그날 메모
	) {
	}
}
