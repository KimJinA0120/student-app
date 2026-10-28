package com.center.app.instructor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 테스트마다 트랜잭션을 롤백하므로 넣은 데이터가 남지 않음
@SpringBootTest
@Transactional
class InstructorScheduleServiceTest {

	// 2026-11-02 월요일
	static final LocalDate MON = LocalDate.of(2026, 11, 2);
	static final LocalDate TUE = MON.plusDays(1);

	@Autowired
	InstructorScheduleService service;

	@Autowired
	InstructorRepository instructorRepository;

	@Autowired
	InstructorOverrideRepository overrideRepository;

	@Autowired
	InstructorDayStatusRepository dayStatusRepository;

	@Autowired
	EntityManager em;

	Instructor kim;
	Instructor lee;
	Instructor park;

	@BeforeEach
	void setUp() {
		instructorRepository.deleteAll();
		// 김: 월 1·2교시 주, 이: 월 1교시 보조, 박: 기본 배치 없음
		kim = new Instructor("김강사", "010-1111-1111");
		kim.addSlot(DayOfWeek.MONDAY, 1, InstructorRole.MAIN, null);
		kim.addSlot(DayOfWeek.MONDAY, 2, InstructorRole.MAIN, null);
		lee = new Instructor("이강사", null);
		lee.addSlot(DayOfWeek.MONDAY, 1, InstructorRole.ASSIST, "오전만");
		park = new Instructor("박강사", null);
		instructorRepository.saveAll(List.of(kim, lee, park));
	}

	@Test
	void 요일_기본_배치로_근무를_계산한다() {
		DaySchedule mon = schedule(MON);

		assertThat(names(mon.period(1).confirmed())).containsExactly("김강사", "이강사"); // 주 먼저
		assertThat(names(mon.period(2).confirmed())).containsExactly("김강사");
		assertThat(mon.period(3).confirmed()).isEmpty();
		assertThat(schedule(TUE).period(1).confirmed()).isEmpty();
	}

	@Test
	void 추가_배치는_더하고_결근은_해당_교시만_뺀다() {
		save(override(park, OverrideType.EXTRA, MON, MON, Set.of(3), "그날만"));
		save(override(kim, OverrideType.ABSENT, MON, MON, Set.of(2), "병원"));

		DaySchedule mon = schedule(MON);

		assertThat(names(mon.period(1).confirmed())).containsExactly("김강사", "이강사");
		assertThat(mon.period(2).confirmed()).isEmpty();
		assertThat(names(mon.period(3).confirmed())).containsExactly("박강사");
		assertThat(mon.absences()).singleElement()
			.satisfies(n -> assertThat(n.name()).isEqualTo("김강사"));
	}

	@Test
	void 기간_결근은_범위_안의_날짜에만_적용된다() {
		// 전 교시(빈 Set), 11/2 ~ 11/9
		save(override(kim, OverrideType.ABSENT, MON, MON.plusDays(7), Set.of(), null));

		assertThat(schedule(MON).period(1).confirmed()).extracting(Entry::name).doesNotContain("김강사");
		assertThat(schedule(MON.plusDays(7)).period(1).confirmed()).extracting(Entry::name).doesNotContain("김강사");
		assertThat(schedule(MON.plusDays(14)).period(1).confirmed()).extracting(Entry::name).contains("김강사");
	}

	@Test
	void 조건부는_따로_표시하고_인원에_넣지_않는다() {
		save(override(park, OverrideType.CONDITIONAL, MON, MON, Set.of(), "11/16부터"));
		save(override(kim, OverrideType.CONDITIONAL, MON, MON, Set.of(), null)); // 이미 확정이면 조건부에서 제외

		PeriodSchedule p1 = schedule(MON).period(1);

		assertThat(p1.count()).isEqualTo(2);
		assertThat(names(p1.conditional())).containsExactly("박강사");
		assertThat(p1.conditional().getFirst().memo()).isEqualTo("11/16부터");
	}

	@Test
	void 조퇴하면_그_교시까지만_근무하고_이후_교시에서_빠진다() {
		dayStatusRepository.save(new InstructorDayStatus(kim, MON, DayStatus.LEAVE_EARLY, 1,
			LocalDateTime.of(MON, java.time.LocalTime.of(11, 0))));
		em.flush();
		em.clear();

		DaySchedule mon = schedule(MON);

		assertThat(mon.period(1).confirmed()).filteredOn(e -> e.name().equals("김강사"))
			.singleElement().extracting(Entry::dayStatus).isEqualTo(DayStatus.LEAVE_EARLY);
		assertThat(mon.period(2).confirmed()).isEmpty();
		// 다음 날에는 영향 없음
		assertThat(schedule(MON.plusDays(7)).period(2).confirmed()).hasSize(1);
	}

	@Test
	void 부족_인원은_8명_기준으로_계산한다() {
		PeriodSchedule p1 = schedule(MON).period(1);

		assertThat(p1.isShort()).isTrue();
		assertThat(p1.shortage()).isEqualTo(6);
	}

	@Test
	void 기간_조회는_날짜마다_결과를_만든다() {
		List<DaySchedule> week = service.getSchedules(MON, MON.plusDays(6));

		assertThat(week).hasSize(7);
		assertThat(week.getFirst().date()).isEqualTo(MON);
		assertThat(week.getLast().date()).isEqualTo(MON.plusDays(6));
	}

	private DaySchedule schedule(LocalDate date) {
		return service.getSchedule(date);
	}

	private InstructorOverride override(Instructor instructor, OverrideType type, LocalDate start, LocalDate end,
			Set<Integer> periods, String memo) {
		return new InstructorOverride(instructor, type, start, end, periods, null, memo);
	}

	// 저장 후 영속성 컨텍스트를 비워 실제 DB에서 다시 읽도록 함
	private void save(InstructorOverride override) {
		overrideRepository.save(override);
		em.flush();
		em.clear();
	}

	private List<String> names(List<Entry> entries) {
		return entries.stream().map(Entry::name).toList();
	}
}
