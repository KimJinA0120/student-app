package com.center.app.instructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.Note;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 강사 근무 계산
// 확정(d, p) = 기본 배치 + 추가 배치 − 결근 − 조퇴(from_period < p)
// 조건부(d, p) = CONDITIONAL 중 확정에 없고 조퇴하지 않은 강사 ("조" 표시)
// 총 인원 = 확정 + 조건부 (부족 판정 기준)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorScheduleService {

	// 교시별 부족 기준 인원
	public static final int REQUIRED_PER_PERIOD = 8;

	// 강사 일정 기간 (전체 달력·이름 검색 범위)
	public static final LocalDate SEASON_START = LocalDate.of(2026, 10, 27);
	public static final LocalDate SEASON_END = LocalDate.of(2026, 12, 10);

	// 주강사 먼저, 같은 역할 안에서는 이름순 (역할 없음은 맨 뒤)
	private static final Comparator<Entry> ENTRY_ORDER = Comparator
		.comparing(Entry::role, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Entry::name);

	private final InstructorRepository instructorRepository;
	private final InstructorOverrideRepository overrideRepository;
	private final InstructorDayStatusRepository dayStatusRepository;

	public DaySchedule getSchedule(LocalDate date) {
		return getSchedules(date, date).getFirst();
	}

	// 기간 전체를 쿼리 3번으로 읽고, 날짜·교시별 계산은 Java에서 함 (데이터가 수백 건 규모)
	public List<DaySchedule> getSchedules(LocalDate from, LocalDate to) {
		Map<Long, Instructor> instructors = instructorRepository.findAllWithSlots().stream()
			.collect(Collectors.toMap(Instructor::getId, Function.identity()));
		List<InstructorOverride> overrides = overrideRepository.findOverlapping(from, to);
		Map<LocalDate, Map<Long, InstructorDayStatus>> statusesByDate = dayStatusRepository
			.findByStatusDateBetween(from, to).stream()
			.collect(Collectors.groupingBy(InstructorDayStatus::getStatusDate,
				Collectors.toMap(s -> s.getInstructor().getId(), Function.identity())));

		List<DaySchedule> result = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			LocalDate date = d; // 람다 안에서 쓰려면 값이 바뀌지 않는 변수여야 함
			List<InstructorOverride> todays = overrides.stream().filter(o -> o.covers(date)).toList();
			Map<Long, InstructorDayStatus> statuses = statusesByDate.getOrDefault(date, Map.of());
			result.add(calculate(date, instructors, todays, statuses));
		}
		return result;
	}

	// 이름에 keyword가 들어간 강사들의 기간 내 일정 (날짜순)
	public List<NameSchedule> searchByName(String keyword) {
		List<Instructor> found = instructorRepository.findByNameContainingOrderByName(keyword);
		if (found.isEmpty()) {
			return List.of();
		}
		List<DaySchedule> days = getSchedules(SEASON_START, SEASON_END);
		return found.stream()
			.map(i -> new NameSchedule(i.getId(), i.getName(), i.getPhone(), dateLines(i.getId(), days)))
			.toList();
	}

	private List<NameSchedule.DateLine> dateLines(Long instructorId, List<DaySchedule> days) {
		List<NameSchedule.DateLine> lines = new ArrayList<>();
		for (DaySchedule day : days) {
			List<Integer> confirmed = new ArrayList<>();
			List<Integer> conditional = new ArrayList<>();
			// 같은 메모가 교시마다 반복되므로 Set으로 한 번만
			Set<String> memos = new LinkedHashSet<>();
			for (PeriodSchedule p : day.periods()) {
				if (find(p.confirmed(), instructorId) != null) {
					confirmed.add(p.period());
				}
				Entry cond = find(p.conditional(), instructorId);
				if (cond != null) {
					conditional.add(p.period());
					if (cond.memo() != null) {
						memos.add("조건부: " + cond.memo());
					}
				}
			}
			for (Note n : day.absences()) {
				if (n.instructorId().equals(instructorId)) {
					memos.add(String.join(" ", "결근", n.periodText(), n.memo() == null ? "" : n.memo()).trim());
				}
			}
			for (Note n : day.notes()) {
				if (n.instructorId().equals(instructorId) && n.memo() != null) {
					memos.add(n.memo());
				}
			}
			if (!confirmed.isEmpty() || !conditional.isEmpty() || !memos.isEmpty()) {
				lines.add(new NameSchedule.DateLine(day.date(), confirmed, conditional, List.copyOf(memos)));
			}
		}
		return lines;
	}

	private Entry find(List<Entry> entries, Long instructorId) {
		return entries.stream().filter(e -> e.instructorId().equals(instructorId)).findFirst().orElse(null);
	}

	private DaySchedule calculate(LocalDate date, Map<Long, Instructor> instructors,
			List<InstructorOverride> overrides, Map<Long, InstructorDayStatus> statuses) {
		List<PeriodSchedule> periods = new ArrayList<>();
		for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
			periods.add(calculatePeriod(date, p, instructors, overrides, statuses));
		}
		Map<Long, Integer> leaveEarly = statuses.values().stream()
			.collect(Collectors.toMap(s -> s.getInstructor().getId(), InstructorDayStatus::getFromPeriod));
		return new DaySchedule(date, periods,
			notesOf(OverrideType.ABSENT, overrides, instructors),
			notesOf(OverrideType.NOTE, overrides, instructors),
			leaveEarly);
	}

	private PeriodSchedule calculatePeriod(LocalDate date, int period, Map<Long, Instructor> instructors,
			List<InstructorOverride> overrides, Map<Long, InstructorDayStatus> statuses) {
		// 강사 id → 근무 항목. 같은 강사가 기본 배치와 추가 배치에 모두 있어도 한 번만 들어감
		Map<Long, Entry> confirmed = new LinkedHashMap<>();

		for (Instructor instructor : instructors.values()) {
			for (InstructorSlot slot : instructor.getSlots()) {
				if (slot.getDayOfWeek() == date.getDayOfWeek() && slot.getPeriod() == period) {
					confirmed.put(instructor.getId(),
						entry(instructor, slot.getRole(), slot.getNote()));
				}
			}
		}
		for (InstructorOverride o : ofType(overrides, OverrideType.EXTRA, date, period)) {
			Instructor instructor = instructors.get(o.getInstructor().getId());
			confirmed.putIfAbsent(instructor.getId(), entry(instructor, o.getRole(), o.getMemo()));
		}
		// 결근으로 빠진 강사는 머리 인원에 "결근 n"으로 따로 보여 줌
		Map<Long, Entry> absent = new LinkedHashMap<>();
		for (InstructorOverride o : ofType(overrides, OverrideType.ABSENT, date, period)) {
			Entry removed = confirmed.remove(o.getInstructor().getId());
			if (removed != null) {
				absent.put(removed.instructorId(), removed);
			}
		}
		Set<Long> leftEarly = statuses.entrySet().stream()
			.filter(s -> s.getValue().excludes(period))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
		confirmed.keySet().removeAll(leftEarly);

		List<Entry> conditional = new ArrayList<>();
		for (InstructorOverride o : ofType(overrides, OverrideType.CONDITIONAL, date, period)) {
			Instructor instructor = instructors.get(o.getInstructor().getId());
			if (!confirmed.containsKey(instructor.getId()) && !leftEarly.contains(instructor.getId())) {
				conditional.add(entry(instructor, o.getRole(), o.getMemo()));
			}
		}

		List<Entry> confirmedList = new ArrayList<>(confirmed.values());
		confirmedList.sort(ENTRY_ORDER);
		conditional.sort(Comparator.comparing(Entry::name));
		List<Entry> absentList = new ArrayList<>(absent.values());
		absentList.sort(Comparator.comparing(Entry::name));
		return new PeriodSchedule(period, confirmedList, conditional, absentList, REQUIRED_PER_PERIOD);
	}

	private List<InstructorOverride> ofType(List<InstructorOverride> overrides, OverrideType type,
			LocalDate date, int period) {
		return overrides.stream()
			.filter(o -> o.getType() == type && o.covers(date, period))
			.toList();
	}

	private Entry entry(Instructor instructor, InstructorRole role, String memo) {
		return new Entry(instructor.getId(), instructor.getName(), instructor.getPhone(), role, memo);
	}

	private List<Note> notesOf(OverrideType type, List<InstructorOverride> overrides,
			Map<Long, Instructor> instructors) {
		return overrides.stream()
			.filter(o -> o.getType() == type)
			.map(o -> {
				Instructor instructor = instructors.get(o.getInstructor().getId());
				return new Note(instructor.getId(), instructor.getName(), new TreeSet<>(o.getPeriods()), o.getMemo());
			})
			.sorted(Comparator.comparing(Note::name))
			.toList();
	}
}
