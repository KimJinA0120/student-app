package com.center.app.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.lesson.LessonTestData;
import com.center.app.student.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorkLogServiceTest {

	static final LocalDate MON = LessonTestData.MON;

	@Autowired
	WorkLogService service;

	@Autowired
	WorkLogRepository repository;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	LessonTestData.Factory factory;

	LessonTestData d;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		d = factory.create();
	}

	static WorkLog.Info info(LocalDate date) {
		return new WorkLog.Info(date, 4, 6, 3, null, 5, 16, " - 1교시 강사 출석 완료 ", "- 205호 wifi 안됨");
	}

	private void lesson(LocalDate date, int period, Student... students) {
		lessonRepository.save(new Lesson(date, period, d.rooms.getFirst(), d.kim, null, null, Set.of(students)));
	}

	@Test
	void 수업_기록으로_강좌_수와_신규_기존을_미리_채운다() {
		// 홍길동은 지난주에 이미 들었으니 기존, 박영희는 오늘이 처음이니 신규 (2교시도 들었지만 1명)
		lesson(MON.minusDays(7), 1, d.hong);
		lesson(MON, 1, d.hong, d.park);
		lesson(MON, 2, d.park);
		lesson(MON, 2, d.hong);
		// 다음 주 수업은 오늘 숫자와 상관없음
		lesson(MON.plusDays(7), 4, d.park);

		assertThat(service.prefill(MON))
			.isEqualTo(new WorkLog.Info(MON, 1, 2, 0, 0, 1, 1, null, null));
	}

	@Test
	void 수업_기록이_없는_날은_숫자를_비워_둔다() {
		assertThat(service.prefill(MON))
			.isEqualTo(new WorkLog.Info(MON, null, null, null, null, null, null, null, null));
	}

	@Test
	void 하루에_한_줄만_쓴다() {
		Long id = service.create(info(MON));
		assertThatThrownBy(() -> service.create(info(MON)))
			.hasMessage("11/2 업무일지가 이미 있습니다. 목록에서 그 날짜를 눌러 수정하세요.");

		// 자기 자신은 같은 날짜로 저장해도 됨, 다른 날 일지의 날짜로 바꾸면 막음
		service.update(id, info(MON));
		Long other = service.create(info(MON.plusDays(1)));
		assertThatThrownBy(() -> service.update(other, info(MON)))
			.hasMessageContaining("이미 있습니다");
	}

	@Test
	void 입력값을_정리하고_검사한다() {
		Long id = service.create(info(MON));
		assertThat(service.get(id).getInstructorNote()).isEqualTo("- 1교시 강사 출석 완료");

		assertThatThrownBy(() -> service.create(info(null))).hasMessage("날짜를 입력하세요.");
		assertThatThrownBy(() -> service.create(new WorkLog.Info(MON.plusDays(1), -1, null, null, null, null, null,
			null, null))).hasMessage("1교시 강좌 수는 0~999 사이로 입력하세요.");
	}

	@Test
	void 계와_누적_합계는_계산한다() {
		service.create(info(MON));
		service.create(new WorkLog.Info(MON.plusDays(1), null, null, null, null, null, null, null, "특이사항만"));

		List<WorkLog> logs = service.list();
		assertThat(logs).extracting(WorkLog::getLogDate).containsExactly(MON.plusDays(1), MON);
		assertThat(logs.get(1).lessonTotal()).isEqualTo(13);
		assertThat(logs.get(1).studentTotal()).isEqualTo(21);
		assertThat(logs.get(0).lessonTotal()).isNull();
		assertThat(WorkLogService.totals(logs)).isEqualTo(new WorkLogService.Totals(13, 5, 16, 21, 2));
	}

	@Test
	void 카톡_글은_비어_있는_항목을_뺀다() {
		WorkLog full = new WorkLog(new WorkLog.Info(LocalDate.of(2026, 10, 29), 4, 6, 3, null, 5, 16,
			"- 1교시 강사 출석 완료", "- 205호 wifi 안됨\n- 전등 수리 완료"));
		assertThat(full.kakaoText()).isEqualTo("""
			[안양 상설교육장 업무일지]
			10/29(목)

			■ 수업 강좌 13개
			  1교시 4 · 2교시 6 · 3교시 3 · 4교시 0
			■ 수강생 21명 (신규 5 · 기존 16)

			■ 강사
			- 1교시 강사 출석 완료

			■ 특이사항
			- 205호 wifi 안됨
			- 전등 수리 완료""");

		WorkLog noteOnly = new WorkLog(new WorkLog.Info(LocalDate.of(2026, 10, 31), null, null, null, null, null,
			null, null, "휴관"));
		assertThat(noteOnly.kakaoText()).isEqualTo("""
			[안양 상설교육장 업무일지]
			10/31(토)

			■ 특이사항
			휴관""");
	}
}
