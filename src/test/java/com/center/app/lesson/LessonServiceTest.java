package com.center.app.lesson;

import static com.center.app.lesson.LessonTestData.MON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Set;

import com.center.app.common.FixedClockConfig;
import com.center.app.lesson.LessonService.LessonInput;
import com.center.app.lesson.LessonService.SaveResult;
import com.center.app.student.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// 지금 = 11/2(월) 14:00, 2교시
@SpringBootTest
@Import(FixedClockConfig.class)
@Transactional
class LessonServiceTest {

	@Autowired
	LessonService service;

	@Autowired
	LessonTestData.Factory factory;

	@Autowired
	EntityManager em;

	LessonTestData d;

	@BeforeEach
	void setUp() {
		d = factory.create();
	}

	LessonInput input(int period, int room, Long instructorId, LocalTime start, LocalTime end, Student... students) {
		return input(MON, period, room, instructorId, start, end, students);
	}

	LessonInput input(java.time.LocalDate date, int period, int room, Long instructorId, LocalTime start,
			LocalTime end, Student... students) {
		return new LessonInput(date, period, d.rooms.get(room).getId(), instructorId, start, end,
			java.util.Arrays.stream(students).map(Student::getId).collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void 수업을_저장한다() {
		SaveResult r = service.create(input(2, 0, d.kim.getId(), null, null, d.hong, d.park));
		em.flush();
		em.clear();

		Lesson saved = service.get(r.id());
		assertThat(r.warning()).isNull();
		assertThat(saved.getStudents()).extracting(Student::getName).containsExactlyInAnyOrder("홍길동", "박영희");
		assertThat(saved.effectiveStart()).isEqualTo(LocalTime.of(13, 0));
		assertThat(saved.effectiveEnd()).isEqualTo(LocalTime.of(15, 30));
	}

	@Test
	void 필수값과_시각을_검증한다() {
		assertThatThrownBy(() -> service.create(input(2, 0, d.kim.getId(), null, null)))
			.hasMessage("수강생을 1명 이상 선택하세요.");
		assertThatThrownBy(() -> service.create(input(2, 0, null, null, null, d.hong)))
			.hasMessage("강사를 선택하세요.");
		assertThatThrownBy(() -> service.create(input(2, 0, d.kim.getId(), LocalTime.of(12, 30), null, d.hong)))
			.hasMessageContaining("시작 시각이 교시 시간 밖입니다");
		assertThatThrownBy(() -> service.create(input(2, 0, d.kim.getId(), null, LocalTime.of(16, 0), d.hong)))
			.hasMessageContaining("종료 시각이 교시 시간 밖입니다");
		assertThatThrownBy(() -> service.create(input(2, 0, d.kim.getId(), LocalTime.of(14, 0), LocalTime.of(13, 30), d.hong)))
			.hasMessage("종료 시각은 시작 시각보다 늦어야 합니다.");
	}

	@Test
	void 지금_교시에_같은_강사나_교실이_수업_중이면_막는다() {
		service.create(input(2, 0, d.kim.getId(), null, null, d.hong));

		assertThatThrownBy(() -> service.create(input(2, 1, d.kim.getId(), null, null, d.park)))
			.hasMessageContaining("김강사 강사님은 같은 시간에 203호에서 수업 중입니다.");
		assertThatThrownBy(() -> service.create(input(2, 0, d.lee.getId(), null, null, d.park)))
			.hasMessageContaining("203호은(는) 같은 시간에 김강사 강사님 수업이 있습니다.");
		assertThat(service.create(input(2, 1, d.lee.getId(), null, null, d.park)).warning()).isNull();
	}

	@Test
	void 중간에_종료한_수업_뒤에는_같은_교시에_새_수업을_시작할_수_있다() {
		// 13:00~13:50에 끝난 수업 → 지금(14:00) 같은 강사·교실로 새 수업
		service.create(input(2, 0, d.kim.getId(), null, LocalTime.of(13, 50), d.hong));

		assertThat(service.create(input(2, 0, d.kim.getId(), null, null, d.park)).warning()).isNull();
	}

	@Test
	void 수정할_때는_자기_자신과_겹치지_않는다() {
		Long id = service.create(input(2, 0, d.kim.getId(), null, null, d.hong)).id();

		service.update(id, input(2, 0, d.kim.getId(), null, null, d.hong, d.park));
		em.flush();
		em.clear();
		assertThat(service.get(id).getStudents()).hasSize(2);
	}

	@Test
	void 지난_기록은_겹쳐도_저장하고_경고만_한다() {
		service.create(input(1, 0, d.kim.getId(), null, null, d.hong));

		SaveResult r = service.create(input(1, 0, d.kim.getId(), null, null, d.park));
		assertThat(r.id()).isNotNull();
		assertThat(r.warning()).startsWith("저장했지만 확인하세요:").contains("김강사 강사님은");
	}

	@Test
	void 종료는_진행_중인_수업만_지금_시각으로_기록한다() {
		Long now = service.create(input(2, 0, d.kim.getId(), null, null, d.hong)).id();
		Long earlier = service.create(input(1, 0, d.kim.getId(), null, null, d.hong)).id();
		Long later = service.create(input(3, 0, d.kim.getId(), null, null, d.hong)).id();

		service.end(now);
		assertThat(service.get(now).getEndTime()).isEqualTo(LocalTime.of(14, 0));
		assertThatThrownBy(() -> service.end(now)).hasMessageContaining("이미 끝난 수업");
		assertThatThrownBy(() -> service.end(earlier)).hasMessageContaining("이미 끝난 수업");
		assertThatThrownBy(() -> service.end(later)).hasMessageContaining("아직 시작하지 않은 수업");
	}

	@Test
	void 수강생_추가와_삭제() {
		Long id = service.create(input(2, 0, d.kim.getId(), null, null, d.hong)).id();

		service.addStudents(id, Set.of(d.park.getId(), d.hong.getId()));
		em.flush();
		em.clear();
		assertThat(service.get(id).getStudents()).hasSize(2);
		assertThatThrownBy(() -> service.addStudents(id, Set.of())).hasMessage("추가할 수강생을 선택하세요.");

		service.delete(id);
		em.flush();
		assertThat(service.list("")).isEmpty();
	}

	@Test
	void 목록은_최근순이고_수강생이나_강사_이름으로_검색한다() {
		service.create(input(MON.minusDays(1), 1, 0, d.lee.getId(), null, null, d.park));
		service.create(input(2, 0, d.kim.getId(), null, null, d.hong));
		em.flush();
		em.clear();

		assertThat(service.list("")).extracting(LessonRow::instructorName).containsExactly("김강사", "이강사");
		assertThat(service.list("영희")).extracting(LessonRow::instructorName).containsExactly("이강사");
		assertThat(service.list("김")).extracting(LessonRow::studentNames).containsExactly(java.util.List.of("홍길동"));
	}
}
