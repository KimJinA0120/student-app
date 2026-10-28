package com.center.app.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.center.app.checkitem.CheckItemRepository;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.InstructorOverride;
import com.center.app.instructor.InstructorOverrideRepository;
import com.center.app.instructor.InstructorRole;
import com.center.app.instructor.InstructorScheduleService;
import com.center.app.instructor.OverrideType;
import com.center.app.migration.LegacySchedule.LegacyDay;
import com.center.app.migration.LegacySchedule.WeeklyRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LegacyScheduleImporterTest {

	// 개인정보라 저장소에 없는 파일. 없으면 실제 데이터 테스트는 건너뜀
	static final Path LEGACY_HTML = Path.of("docs/안양_강사일정_요일별_전체.html");

	static final LocalDate FRI_1030 = LocalDate.of(2026, 10, 30);
	static final LocalDate FRI_1106 = LocalDate.of(2026, 11, 6);

	@Autowired
	LegacyScheduleImporter importer;

	@Autowired
	InstructorScheduleService scheduleService;

	@Autowired
	InstructorOverrideRepository overrideRepository;

	@Autowired
	CheckItemRepository checkItemRepository;

	@Test
	void 날짜별_명단이_그대로_재현된다() {
		// 금 1교시 기본 배치: A, B, C
		// 10/30 금: A, B, C / 11/6 금: A, C, D → 11/6에 B 결근, D 추가 배치
		var weekly = Map.of(DayOfWeek.FRIDAY, List.of(List.of(
			new WeeklyRow("A", InstructorRole.MAIN, null),
			new WeeklyRow("B", InstructorRole.ASSIST, null),
			new WeeklyRow("C", InstructorRole.ASSIST, null))));
		var legacy = new LegacySchedule(Map.of(), weekly, List.of(
			day(FRI_1030, List.of("A", "B", "C"), List.of()),
			day(FRI_1106, List.of("A", "C", "D"), List.of("B 개인 사정"))
		), List.of());

		importer.importFrom(legacy);

		assertThat(names(FRI_1030)).containsExactlyInAnyOrder("A", "B", "C");
		assertThat(names(FRI_1106)).containsExactlyInAnyOrder("A", "C", "D");

		List<InstructorOverride> overrides = overrideRepository.findAll();
		assertThat(overrides).hasSize(2);
		assertThat(overrides).filteredOn(o -> o.getType() == OverrideType.ABSENT).singleElement()
			.satisfies(o -> {
				assertThat(o.getInstructor().getName()).isEqualTo("B");
				assertThat(o.getStartDate()).isEqualTo(FRI_1106);
				assertThat(o.getPeriods()).containsExactly(1);
				assertThat(o.getMemo()).isEqualTo("개인 사정"); // 그날 메모가 결근 메모로 붙음
			});
	}

	@Test
	void 실제_HTML_전체를_이전하면_원본과_같다() throws Exception {
		assumeTrue(Files.exists(LEGACY_HTML), "기존 일정 HTML 없음");
		LegacySchedule legacy = new LegacyScheduleParser().parse(Files.readString(LEGACY_HTML));

		// 날짜·교시별 확정·조건부 비교는 importFrom 안의 검증에서 함 (다르면 예외)
		LegacyScheduleImporter.Result result = importer.importFrom(legacy);

		assertThat(legacy.days()).hasSize(45);
		assertThat(result.instructors()).isEqualTo(legacy.phones().size());
		assertThat(result.checkItems()).isEqualTo(9);
		assertThat(checkItemRepository.findAll()).allSatisfy(c -> assertThat(c.isResolved()).isFalse());
	}

	private LegacyDay day(LocalDate date, List<String> period1, List<String> notes) {
		return new LegacyDay(date, List.of(period1, List.of(), List.of(), List.of()),
			List.of(List.of(), List.of(), List.of(), List.of()), notes);
	}

	private List<String> names(LocalDate date) {
		return scheduleService.getSchedule(date).period(1).confirmed().stream().map(Entry::name).toList();
	}
}
