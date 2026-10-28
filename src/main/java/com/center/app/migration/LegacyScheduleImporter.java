package com.center.app.migration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.center.app.checkitem.CheckItem;
import com.center.app.checkitem.CheckItemRepository;
import com.center.app.common.PeriodUtil;
import com.center.app.instructor.DaySchedule;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorOverride;
import com.center.app.instructor.InstructorOverrideRepository;
import com.center.app.instructor.InstructorRepository;
import com.center.app.instructor.InstructorRole;
import com.center.app.instructor.InstructorScheduleService;
import com.center.app.instructor.OverrideType;
import com.center.app.migration.LegacySchedule.Conditional;
import com.center.app.migration.LegacySchedule.LegacyDay;
import com.center.app.migration.LegacySchedule.WeeklyRow;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 기존 HTML 데이터를 강사·기본 배치·날짜별 예외·확인 필요로 변환해 저장
// - 기본 배치 = 요일별 보기 표
// - 날짜별 명단과 기본 배치의 차이 → 결근(ABSENT) / 추가 배치(EXTRA)
// - 저장 후 계산 결과가 원본 명단과 날짜·교시마다 같은지 검증하고, 다르면 전부 롤백
@Service
@RequiredArgsConstructor
public class LegacyScheduleImporter {

	private final InstructorRepository instructorRepository;
	private final InstructorOverrideRepository overrideRepository;
	private final CheckItemRepository checkItemRepository;
	private final InstructorScheduleService scheduleService;
	private final EntityManager em;

	public record Result(int instructors, int slots, int overrides, int checkItems) {
	}

	// 하루 단위 예외 (같은 강사·종류·날짜·메모면 교시를 모음)
	private record DayKey(String name, OverrideType type, LocalDate date, InstructorRole role, String memo) {

		DayKey withMemo(String memo) {
			return new DayKey(name, type, date, role, memo);
		}
	}

	// 날짜만 다른 예외를 연속 기간으로 합치기 위한 키
	private record RangeKey(String name, OverrideType type, InstructorRole role, String memo, Set<Integer> periods) {
	}

	@Transactional
	public Result importFrom(LegacySchedule legacy) {
		if (instructorRepository.count() > 0) {
			throw new IllegalStateException("이미 강사 데이터가 있어 이전하지 않습니다.");
		}
		Map<String, Instructor> instructors = createInstructors(legacy);
		instructorRepository.saveAll(instructors.values());

		List<InstructorOverride> overrides = createOverrides(legacy, instructors);
		overrideRepository.saveAll(overrides);

		List<CheckItem> checkItems = legacy.checkItems().stream()
			.map(text -> new CheckItem(text, personOf(text, instructors.keySet())))
			.toList();
		checkItemRepository.saveAll(checkItems);

		// DB에 반영한 뒤 메모리를 비워서, 검증은 실제로 저장된 값을 다시 읽어서 함
		em.flush();
		em.clear();
		verify(legacy);

		int slots = instructors.values().stream().mapToInt(i -> i.getSlots().size()).sum();
		return new Result(instructors.size(), slots, overrides.size(), checkItems.size());
	}

	private Map<String, Instructor> createInstructors(LegacySchedule legacy) {
		Set<String> names = new TreeSet<>(legacy.phones().keySet());
		legacy.weekly().values().forEach(periods -> periods.forEach(rows -> rows.forEach(r -> names.add(r.name()))));
		for (LegacyDay day : legacy.days()) {
			day.confirmed().forEach(names::addAll);
			day.conditional().forEach(list -> list.forEach(c -> names.add(c.name())));
		}

		Map<String, Instructor> instructors = new TreeMap<>();
		names.forEach(name -> instructors.put(name, new Instructor(name, legacy.phones().get(name))));
		legacy.weekly().forEach((dayOfWeek, periods) -> {
			for (int p = PeriodUtil.FIRST; p <= periods.size(); p++) {
				for (WeeklyRow row : periods.get(p - 1)) {
					instructors.get(row.name()).addSlot(dayOfWeek, p, row.role(), row.note());
				}
			}
		});
		return instructors;
	}

	private List<InstructorOverride> createOverrides(LegacySchedule legacy, Map<String, Instructor> instructors) {
		Map<String, InstructorRole> roles = mainRoles(legacy);
		Map<DayKey, Set<Integer>> perDay = new LinkedHashMap<>();

		for (LegacyDay day : legacy.days()) {
			for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
				Set<String> slot = weeklyNames(legacy, day.date().getDayOfWeek(), p);
				Set<String> confirmed = new HashSet<>(day.confirmed().get(p - 1));
				for (String name : slot) {
					if (!confirmed.contains(name)) {
						add(perDay, new DayKey(name, OverrideType.ABSENT, day.date(), null, null), p);
					}
				}
				for (String name : confirmed) {
					if (!slot.contains(name)) {
						add(perDay, new DayKey(name, OverrideType.EXTRA, day.date(), roles.get(name), null), p);
					}
				}
				for (Conditional c : day.conditional().get(p - 1)) {
					add(perDay, new DayKey(c.name(), OverrideType.CONDITIONAL, day.date(), null, c.memo()), p);
				}
			}
			attachNotes(perDay, day, instructors.keySet());
		}
		return mergeIntoRanges(perDay, instructors);
	}

	// "이름 내용" 메모: 그날 그 강사의 결근이 있으면 결근 메모로, 없으면 메모(NOTE)로
	private void attachNotes(Map<DayKey, Set<Integer>> perDay, LegacyDay day, Set<String> names) {
		Map<String, List<String>> notesByName = new LinkedHashMap<>();
		for (String note : day.notes()) {
			String name = personOf(note.replaceFirst(" ", ":"), names);
			if (name == null) {
				throw new IllegalArgumentException(day.date() + " 메모의 강사를 찾을 수 없습니다: " + note);
			}
			notesByName.computeIfAbsent(name, k -> new ArrayList<>()).add(note.substring(name.length()).trim());
		}
		notesByName.forEach((name, memos) -> {
			String memo = String.join(" / ", memos);
			DayKey absent = new DayKey(name, OverrideType.ABSENT, day.date(), null, null);
			if (perDay.containsKey(absent)) {
				perDay.put(absent.withMemo(memo), perDay.remove(absent));
			} else {
				perDay.put(new DayKey(name, OverrideType.NOTE, day.date(), null, memo), new TreeSet<>());
			}
		});
	}

	// 같은 내용이 연속된 날짜에 이어지면 한 행(시작일~종료일)으로, 띄엄띄엄이면 날짜마다 한 행
	private List<InstructorOverride> mergeIntoRanges(Map<DayKey, Set<Integer>> perDay,
			Map<String, Instructor> instructors) {
		Map<RangeKey, TreeSet<LocalDate>> datesByKey = new LinkedHashMap<>();
		perDay.forEach((k, periods) -> {
			// 4교시 전부면 빈 Set(= 전 교시)으로 저장
			Set<Integer> normalized = periods.size() == PeriodUtil.LAST ? new TreeSet<>() : periods;
			datesByKey.computeIfAbsent(new RangeKey(k.name(), k.type(), k.role(), k.memo(), normalized),
				x -> new TreeSet<>()).add(k.date());
		});

		List<InstructorOverride> overrides = new ArrayList<>();
		datesByKey.forEach((k, dates) -> {
			LocalDate start = null;
			LocalDate end = null;
			for (LocalDate date : dates) {
				if (start != null && date.equals(end.plusDays(1))) {
					end = date;
					continue;
				}
				if (start != null) {
					overrides.add(override(k, instructors, start, end));
				}
				start = date;
				end = date;
			}
			overrides.add(override(k, instructors, start, end));
		});
		overrides.sort(Comparator.comparing(InstructorOverride::getStartDate));
		return overrides;
	}

	private InstructorOverride override(RangeKey k, Map<String, Instructor> instructors, LocalDate start,
			LocalDate end) {
		return new InstructorOverride(instructors.get(k.name()), k.type(), start, end, k.periods(), k.role(),
			k.memo());
	}

	// 추가 배치 표시용 역할: 그 강사가 기본 배치에서 가장 많이 맡은 역할 (기본 배치가 없으면 null)
	private Map<String, InstructorRole> mainRoles(LegacySchedule legacy) {
		Map<String, Map<InstructorRole, Long>> counts = new HashMap<>();
		legacy.weekly().values().forEach(periods -> periods.forEach(rows -> rows.forEach(r ->
			counts.computeIfAbsent(r.name(), k -> new HashMap<>()).merge(r.role(), 1L, Long::sum))));
		Map<String, InstructorRole> roles = new HashMap<>();
		counts.forEach((name, byRole) -> roles.put(name,
			byRole.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()));
		return roles;
	}

	private Set<String> weeklyNames(LegacySchedule legacy, DayOfWeek dayOfWeek, int period) {
		List<List<WeeklyRow>> periods = legacy.weekly().getOrDefault(dayOfWeek, List.of());
		if (periods.size() < period) {
			return Set.of();
		}
		return periods.get(period - 1).stream().map(WeeklyRow::name).collect(Collectors.toSet());
	}

	private void add(Map<DayKey, Set<Integer>> perDay, DayKey key, int period) {
		perDay.computeIfAbsent(key, k -> new TreeSet<>()).add(period);
	}

	// "이름: 내용"에서 이름이 강사 이름이면 반환
	private String personOf(String text, Set<String> names) {
		int colon = text.indexOf(':');
		if (colon < 0) {
			return null;
		}
		String name = text.substring(0, colon).trim();
		return names.contains(name) ? name : null;
	}

	private void verify(LegacySchedule legacy) {
		LocalDate from = legacy.days().stream().map(LegacyDay::date).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate to = legacy.days().stream().map(LegacyDay::date).max(Comparator.naturalOrder()).orElseThrow();
		Map<LocalDate, DaySchedule> schedules = scheduleService.getSchedules(from, to).stream()
			.collect(Collectors.toMap(DaySchedule::date, Function.identity()));

		List<String> errors = new ArrayList<>();
		for (LegacyDay day : legacy.days()) {
			DaySchedule schedule = schedules.get(day.date());
			for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
				Set<String> expected = new TreeSet<>(day.confirmed().get(p - 1));
				Set<String> actual = schedule.period(p).confirmed().stream()
					.map(Entry::name).collect(Collectors.toCollection(TreeSet::new));
				if (!expected.equals(actual)) {
					errors.add(day.date() + " " + p + "교시 확정: 원본 " + expected + " / 계산 " + actual);
				}
				Set<Conditional> expectedCond = new HashSet<>(day.conditional().get(p - 1));
				Set<Conditional> actualCond = schedule.period(p).conditional().stream()
					.map(e -> new Conditional(e.name(), e.memo())).collect(Collectors.toSet());
				if (!expectedCond.equals(actualCond)) {
					errors.add(day.date() + " " + p + "교시 조건부: 원본 " + expectedCond + " / 계산 " + actualCond);
				}
			}
		}
		if (!errors.isEmpty()) {
			throw new IllegalStateException("이전 결과가 원본과 다릅니다 (" + errors.size() + "건):\n"
				+ String.join("\n", errors.subList(0, Math.min(20, errors.size()))));
		}
	}
}
