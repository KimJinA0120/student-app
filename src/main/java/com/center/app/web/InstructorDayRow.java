package com.center.app.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.DaySchedule;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import com.center.app.instructor.InstructorRole;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.RefPoint;
import com.center.app.lesson.Reservation;
import com.center.app.student.Student;

// 강사 시간표의 한 줄: 강사 한 명의 1~4교시 칸 (근무 표시 + 예약 + 그 칸의 수업 기록 + 버튼)
// DaySchedule은 교시 → 강사 목록 구조라, 화면용으로 강사 → 교시 구조로 뒤집음
public record InstructorDayRow(
	Long instructorId,
	String name,
	String roles,      // "주", "보조", "주·보조", 조건부만 있으면 "조건부", 배치가 없으면 "배치 외"
	List<Cell> cells,  // 1~4교시 순서
	boolean hasMain
) {

	// CONFIRMED 확정 / CONDITIONAL 조건부 / LEFT 조퇴 후 교시 / NONE 근무 없음
	public enum Mark { CONFIRMED, CONDITIONAL, LEFT, NONE }

	// 시간표 한 칸
	public record Cell(
		int period,
		Mark mark,
		boolean leaveHere,          // 이 교시에 [조퇴]를 누름 → "조퇴" + [취소]
		List<LessonCell> lessons,
		String reservation,         // 메모 예약 한 줄. 수업 기록이 생기면 숨김(null)
		List<ReservedStudent> reservedStudents,  // 예약(등록 수강생, 이름순). 수업 기록이 생기면 숨김(빈 목록)
		boolean canReserve,         // 메모·예약 입력칸 (근무 칸 · 수업 기록 없음 · 지나지 않은 교시)
		boolean canStart,           // [수업시작]
		boolean canLeave            // [조퇴]
	) {

		// 근무하는 칸만 수업을 넣을 수 있음 (근무 없는 칸·조퇴 후 칸은 회색으로 막음)
		public boolean available() {
			return mark == Mark.CONFIRMED || mark == Mark.CONDITIONAL;
		}

		// 메모 또는 예약 수강생이 있음
		public boolean hasReservation() {
			return reservation != null || !reservedStudents.isEmpty();
		}

		public List<Long> reservedStudentIds() {
			return reservedStudents.stream().map(ReservedStudent::id).toList();
		}

		public String reservedStudentNames() {
			return names(reservedStudents);
		}
	}

	// 예약에 고른 등록 수강생. [수업시작] 폼에 id로 넘겨 미리 체크
	public record ReservedStudent(Long id, String name) {
	}

	// 맨 위 "미배정" 줄의 한 칸. 강사 없이 날짜·교시만 정한 메모 예약·예약
	// editable = 지나지 않은 교시 (입력칸), candidates = [배정]할 수 있는 그 교시 근무 강사 (확정·조건부)
	public record UnassignedCell(int period, String reservation, List<ReservedStudent> reservedStudents,
			boolean editable, List<Entry> candidates) {

		public boolean hasReservation() {
			return reservation != null || !reservedStudents.isEmpty();
		}

		public String reservedStudentNames() {
			return names(reservedStudents);
		}
	}

	private static String names(List<ReservedStudent> students) {
		return String.join(", ", students.stream().map(ReservedStudent::name).toList());
	}

	private static List<ReservedStudent> reservedStudents(Reservation r) {
		return r.getStudents().stream().map(st -> new ReservedStudent(st.getId(), st.getName()))
			.sorted(Comparator.comparing(ReservedStudent::name)).toList();
	}

	private static String memo(Reservation r) {
		return r.getContent().isEmpty() ? null : r.getContent();
	}

	// 칸 안의 수업 기록 한 건. ongoing = 지금 진행 중 → [종료] 가능
	public record LessonCell(Long lessonId, String roomName, List<String> studentNames, String timeText,
			boolean ongoing) {
	}

	// 주강사 줄 먼저, 그 안에서 그날 근무 교시(확정 + 조건부)가 많은 순 → 확정 교시가 많은 순 → 이름순
	// 결근·조퇴로 빠진 교시는 세지 않음 (조퇴하면 그 강사 줄이 아래로 내려감). 배치 외 줄(0교시)은 맨 뒤
	private static final Comparator<InstructorDayRow> ORDER = Comparator
		.comparing((InstructorDayRow r) -> !r.hasMain())
		.thenComparingLong(r -> -r.count(Cell::available))
		.thenComparingLong(r -> -r.count(c -> c.mark() == Mark.CONFIRMED))
		.thenComparing(InstructorDayRow::name);

	private long count(Predicate<Cell> filter) {
		return cells.stream().filter(filter).count();
	}

	public static List<InstructorDayRow> of(DaySchedule day, List<Lesson> lessons, List<Reservation> reservations,
			LocalDateTime now) {
		// 강사 id → 교시별로 모은 정보 (등장 순서 유지)
		Map<Long, Builder> builders = new LinkedHashMap<>();
		for (PeriodSchedule p : day.periods()) {
			for (Entry e : p.confirmed()) {
				builders.computeIfAbsent(e.instructorId(), id -> new Builder(id, e.name())).add(p.period(), Mark.CONFIRMED, e);
			}
			for (Entry e : p.conditional()) {
				builders.computeIfAbsent(e.instructorId(), id -> new Builder(id, e.name())).add(p.period(), Mark.CONDITIONAL, e);
			}
		}
		// 배치표에는 없지만 수업 기록이 있는 강사(지난 기록 등)도 줄을 만들어야 기록을 보고 고칠 수 있음
		for (Lesson l : lessons) {
			builders.computeIfAbsent(l.getInstructor().getId(), id -> new Builder(id, l.getInstructor().getName()))
				.lessons.get(l.getPeriod() - 1).add(l);
		}
		// 예약 후 결근 등으로 근무가 빠진 강사도 줄이 있어야 예약을 확인하고 지울 수 있음
		for (Reservation r : reservations) {
			if (r.getInstructor() == null) {
				continue; // 미배정은 unassigned()에서
			}
			builders.computeIfAbsent(r.getInstructor().getId(), id -> new Builder(id, r.getInstructor().getName()))
				.reservations[r.getPeriod() - 1] = r;
		}
		List<InstructorDayRow> rows = new ArrayList<>();
		builders.values().forEach(b -> rows.add(b.build(day, now)));
		rows.sort(ORDER);
		return rows;
	}

	public static List<UnassignedCell> unassigned(DaySchedule day, List<Reservation> reservations,
			LocalDateTime now) {
		Reservation[] slots = new Reservation[4];
		reservations.stream().filter(r -> r.getInstructor() == null)
			.forEach(r -> slots[r.getPeriod() - 1] = r);
		List<UnassignedCell> cells = new ArrayList<>();
		for (PeriodSchedule p : day.periods()) {
			List<Entry> candidates = new ArrayList<>(p.confirmed());
			candidates.addAll(p.conditional());
			candidates.sort(Comparator.comparing(Entry::name));
			Reservation r = slots[p.period() - 1];
			cells.add(new UnassignedCell(p.period(), r == null ? null : memo(r),
				r == null ? List.of() : reservedStudents(r), !RefPoint.isPast(day.date(), p.period(), now), candidates));
		}
		return cells;
	}

	private static class Builder {
		private final Long id;
		private final String name;
		private final Mark[] marks = {Mark.NONE, Mark.NONE, Mark.NONE, Mark.NONE};
		private final List<List<Lesson>> lessons = List.of(new ArrayList<>(), new ArrayList<>(),
			new ArrayList<>(), new ArrayList<>());
		private final Reservation[] reservations = new Reservation[4];
		private final Set<InstructorRole> roles = new LinkedHashSet<>();

		Builder(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		void add(int period, Mark mark, Entry e) {
			marks[period - 1] = mark;
			if (mark == Mark.CONFIRMED && e.role() != null) {
				roles.add(e.role());
			}
		}

		InstructorDayRow build(DaySchedule day, LocalDateTime now) {
			Integer leaveFrom = day.leaveEarly().get(id);
			List<Cell> cells = new ArrayList<>();
			for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
				Mark mark = marks[p - 1];
				if (mark == Mark.NONE && leaveFrom != null && leaveFrom < p) {
					mark = Mark.LEFT;
				}
				cells.add(cell(day.date(), p, mark, leaveFrom, lessons.get(p - 1), reservations[p - 1], now));
			}
			boolean confirmed = List.of(marks).contains(Mark.CONFIRMED);
			boolean conditional = List.of(marks).contains(Mark.CONDITIONAL);
			String roleText = roles.stream().sorted().map(InstructorRole::getLabel)
				.reduce((a, b) -> a + "·" + b)
				.orElse(confirmed ? "" : conditional ? "조건부" : "배치 외");
			return new InstructorDayRow(id, name, roleText, cells, roles.contains(InstructorRole.MAIN));
		}

		private Cell cell(LocalDate date, int period, Mark mark, Integer leaveFrom, List<Lesson> list,
				Reservation reservation, LocalDateTime now) {
			boolean available = mark == Mark.CONFIRMED || mark == Mark.CONDITIONAL;
			boolean past = RefPoint.isPast(date, period, now);
			LocalTime nowTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
			boolean today = date.equals(now.toLocalDate());

			List<LessonCell> lessonCells = list.stream().map(l -> new LessonCell(l.getId(), l.getRoom().getName(),
				l.getStudents().stream().map(Student::getName).sorted().toList(), timeText(l),
				today && nowTime.isAfter(l.effectiveStart()) && nowTime.isBefore(l.effectiveEnd()))).toList();
			// 아직 안 끝난 수업이 있으면 [수업시작]을 숨김 (중간에 [종료]하면 다시 보임)
			// 지난 칸은 기록이 하나도 없을 때만 (수기 기록 옮겨 넣기용)
			boolean unfinished = list.stream()
				.anyMatch(l -> date.isAfter(now.toLocalDate()) || today && nowTime.isBefore(l.effectiveEnd()));
			boolean canStart = available && (past ? list.isEmpty() : !unfinished);
			// 조퇴는 오늘, 아직 안 끝난 근무 칸에서만. 한 번 누르면 그 칸에 [취소]가 대신 보임
			boolean canLeave = available && today && !past && leaveFrom == null;
			// 메모 예약·예약은 수업 기록이 생기면 숨김 (DB에는 남음)
			boolean showReservation = list.isEmpty() && reservation != null;
			String shownReservation = showReservation ? memo(reservation) : null;
			List<ReservedStudent> reserved = showReservation ? reservedStudents(reservation) : List.of();
			boolean canReserve = available && list.isEmpty() && !past;
			return new Cell(period, mark, leaveFrom != null && leaveFrom == period, lessonCells, shownReservation,
				reserved, canReserve, canStart, canLeave);
		}

		// 시작·종료 시각을 따로 적은 수업만 "13:30~14:20"처럼 표시 (비우면 교시 시간이라 생략)
		private static String timeText(Lesson l) {
			if (l.getStartTime() == null && l.getEndTime() == null) {
				return null;
			}
			return l.effectiveStart() + "~" + l.effectiveEnd();
		}
	}
}
