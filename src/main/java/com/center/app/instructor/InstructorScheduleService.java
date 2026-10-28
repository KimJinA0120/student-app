package com.center.app.instructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
// 조건부(d, p) = CONDITIONAL 중 확정에 없는 강사 ("조" 표시, 인원 미포함)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorScheduleService {

	// 교시별 부족 기준 인원
	public static final int REQUIRED_PER_PERIOD = 8;

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

	private DaySchedule calculate(LocalDate date, Map<Long, Instructor> instructors,
			List<InstructorOverride> overrides, Map<Long, InstructorDayStatus> statuses) {
		List<PeriodSchedule> periods = new ArrayList<>();
		for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
			periods.add(calculatePeriod(date, p, instructors, overrides, statuses));
		}
		return new DaySchedule(date, periods,
			notesOf(OverrideType.ABSENT, overrides, instructors),
			notesOf(OverrideType.NOTE, overrides, instructors));
	}

	private PeriodSchedule calculatePeriod(LocalDate date, int period, Map<Long, Instructor> instructors,
			List<InstructorOverride> overrides, Map<Long, InstructorDayStatus> statuses) {
		// 강사 id → 근무 항목. 같은 강사가 기본 배치와 추가 배치에 모두 있어도 한 번만 들어감
		Map<Long, Entry> confirmed = new LinkedHashMap<>();

		for (Instructor instructor : instructors.values()) {
			for (InstructorSlot slot : instructor.getSlots()) {
				if (slot.getDayOfWeek() == date.getDayOfWeek() && slot.getPeriod() == period) {
					confirmed.put(instructor.getId(),
						entry(instructor, slot.getRole(), slot.getNote(), statuses));
				}
			}
		}
		for (InstructorOverride o : ofType(overrides, OverrideType.EXTRA, date, period)) {
			Instructor instructor = instructors.get(o.getInstructor().getId());
			confirmed.putIfAbsent(instructor.getId(), entry(instructor, o.getRole(), o.getMemo(), statuses));
		}
		for (InstructorOverride o : ofType(overrides, OverrideType.ABSENT, date, period)) {
			confirmed.remove(o.getInstructor().getId());
		}
		confirmed.keySet().removeIf(id -> statuses.containsKey(id) && statuses.get(id).excludes(period));

		List<Entry> conditional = new ArrayList<>();
		for (InstructorOverride o : ofType(overrides, OverrideType.CONDITIONAL, date, period)) {
			Instructor instructor = instructors.get(o.getInstructor().getId());
			if (!confirmed.containsKey(instructor.getId())) {
				conditional.add(entry(instructor, o.getRole(), o.getMemo(), statuses));
			}
		}

		List<Entry> confirmedList = new ArrayList<>(confirmed.values());
		confirmedList.sort(ENTRY_ORDER);
		conditional.sort(Comparator.comparing(Entry::name));
		return new PeriodSchedule(period, confirmedList, conditional, REQUIRED_PER_PERIOD);
	}

	private List<InstructorOverride> ofType(List<InstructorOverride> overrides, OverrideType type,
			LocalDate date, int period) {
		return overrides.stream()
			.filter(o -> o.getType() == type && o.covers(date, period))
			.toList();
	}

	private Entry entry(Instructor instructor, InstructorRole role, String memo,
			Map<Long, InstructorDayStatus> statuses) {
		InstructorDayStatus status = statuses.get(instructor.getId());
		return new Entry(instructor.getId(), instructor.getName(), instructor.getPhone(), role, memo,
			status == null ? null : status.getStatus());
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
