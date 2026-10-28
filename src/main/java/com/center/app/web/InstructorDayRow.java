package com.center.app.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.center.app.instructor.DaySchedule;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import com.center.app.instructor.InstructorRole;

// 날짜별 보기 표의 한 줄: 강사 한 명의 1~4교시 근무 표시
// DaySchedule은 교시 → 강사 목록 구조라, 화면용으로 강사 → 교시 구조로 뒤집음
public record InstructorDayRow(
	Long instructorId,
	String name,
	String phone,
	String roles,      // "주", "보조", "주·보조", 조건부만 있으면 "조건부"
	String memo,       // 기본 배치 비고·예외 메모 (중복 제거)
	List<Mark> marks,  // 1~4교시 순서
	boolean hasMain
) {

	public enum Mark { CONFIRMED, CONDITIONAL, NONE }

	// 주강사가 있는 줄 먼저, 조건부만 있는 줄은 맨 뒤, 같은 그룹 안에서는 이름순
	private static final Comparator<InstructorDayRow> ORDER = Comparator
		.comparing((InstructorDayRow r) -> !r.hasMain())
		.thenComparing(r -> !r.marks().contains(Mark.CONFIRMED))
		.thenComparing(InstructorDayRow::name);

	public String tel() {
		return phone == null ? null : phone.replaceAll("[^0-9]", "");
	}

	public static List<InstructorDayRow> of(DaySchedule day) {
		// 강사 id → 교시별로 모은 정보 (등장 순서 유지)
		Map<Long, Builder> builders = new LinkedHashMap<>();
		for (PeriodSchedule p : day.periods()) {
			for (Entry e : p.confirmed()) {
				builders.computeIfAbsent(e.instructorId(), id -> new Builder(e)).add(p.period(), Mark.CONFIRMED, e);
			}
			for (Entry e : p.conditional()) {
				builders.computeIfAbsent(e.instructorId(), id -> new Builder(e)).add(p.period(), Mark.CONDITIONAL, e);
			}
		}
		List<InstructorDayRow> rows = new ArrayList<>();
		builders.values().forEach(b -> rows.add(b.build()));
		rows.sort(ORDER);
		return rows;
	}

	private static class Builder {
		private final Entry first;
		private final Mark[] marks = {Mark.NONE, Mark.NONE, Mark.NONE, Mark.NONE};
		private final Set<InstructorRole> roles = new LinkedHashSet<>();
		private final Set<String> memos = new LinkedHashSet<>();

		Builder(Entry first) {
			this.first = first;
		}

		void add(int period, Mark mark, Entry e) {
			marks[period - 1] = mark;
			if (mark == Mark.CONFIRMED && e.role() != null) {
				roles.add(e.role());
			}
			if (e.memo() != null && !e.memo().isBlank()) {
				memos.add(e.memo());
			}
		}

		InstructorDayRow build() {
			boolean confirmed = List.of(marks).contains(Mark.CONFIRMED);
			String roleText = roles.stream().sorted().map(InstructorRole::getLabel)
				.reduce((a, b) -> a + "·" + b).orElse(confirmed ? "" : "조건부");
			return new InstructorDayRow(first.instructorId(), first.name(), first.phone(), roleText,
				memos.isEmpty() ? null : String.join(" · ", memos), List.of(marks),
				roles.contains(InstructorRole.MAIN));
		}
	}
}
