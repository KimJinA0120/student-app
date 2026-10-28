package com.center.app.instructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 하루치 강사 근무 계산 결과 (DB에 저장하지 않고 조회할 때마다 계산)
// record: 값만 담는 불변 객체. 화면에 엔티티 대신 넘겨서 지연 로딩 문제를 피함
public record DaySchedule(
	LocalDate date,
	List<PeriodSchedule> periods,  // 1~4교시 순서
	List<Note> absences,           // 그날 빠지는 강사 (결근)
	List<Note> notes               // 그날 메모
) {

	public PeriodSchedule period(int period) {
		return periods.get(period - 1);
	}

	public record PeriodSchedule(
		int period,
		List<Entry> confirmed,    // 확정 근무 (인원수에 포함)
		List<Entry> conditional,  // 조건부 "조" (인원수 미포함)
		int required              // 부족 기준 인원
	) {

		public int count() {
			return confirmed.size();
		}

		public boolean isShort() {
			return count() < required;
		}

		public int shortage() {
			return Math.max(0, required - count());
		}
	}

	public record Entry(
		Long instructorId,
		String name,
		String phone,
		InstructorRole role,  // 추가 배치에 역할이 없으면 null
		String memo,          // 기본 배치 비고 또는 예외 메모
		DayStatus dayStatus   // 그날 휴식·외출·조퇴 상태, 없으면 null
	) {
	}

	public record Note(
		Long instructorId,
		String name,
		Set<Integer> periods,  // 빈 Set = 전 교시
		String memo
	) {

		// "1·3교시", 전 교시면 빈 문자열
		public String periodText() {
			return periods.isEmpty() ? "" : periods.stream().map(String::valueOf)
				.collect(Collectors.joining("·")) + "교시";
		}
	}
}
