package com.center.app.lesson;

import static com.center.app.lesson.LessonTestData.MON;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import com.center.app.common.FixedClockConfig;
import com.center.app.instructor.DayStatus;
import com.center.app.instructor.InstructorDayStatus;
import com.center.app.instructor.InstructorDayStatusRepository;
import com.center.app.lesson.PeriodBoard.InstructorLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(FixedClockConfig.class)
@Transactional
class PeriodBoardServiceTest {

	@Autowired
	PeriodBoardService service;

	@Autowired
	LessonTestData.Factory factory;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	InstructorDayStatusRepository dayStatusRepository;

	LessonTestData d;

	@BeforeEach
	void setUp() {
		d = factory.create();
	}

	RefPoint at(int period, LocalTime time) {
		return new RefPoint(MON, period, time, false);
	}

	@Test
	void 근무_강사의_수업_중_상태와_빈_교실을_계산한다() {
		lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.kim, null, null, Set.of(d.hong, d.park)));
		// 배치 밖인 최강사도 수업 기록이 있으면 보임
		lessonRepository.save(new Lesson(MON, 2, d.rooms.get(1), d.choi, null, null, Set.of(d.park)));

		PeriodBoard board = service.board(at(2, LocalTime.of(14, 0)));

		assertThat(board.instructors()).extracting(InstructorLine::name).containsExactly("김강사", "이강사", "최강사");
		InstructorLine kim = board.instructor(d.kim.getId());
		assertThat(kim.busy()).isTrue();
		assertThat(kim.lesson().roomName()).isEqualTo("203호");
		assertThat(kim.lesson().studentCount()).isEqualTo(2);
		assertThat(board.instructor(d.lee.getId()).free()).isTrue();
		assertThat(board.instructor(d.choi.getId()).scheduled()).isFalse();
		// 203호·204호 사용 중 → 205호 자동 배정
		assertThat(board.firstFreeRoomId()).isEqualTo(d.rooms.get(2).getId());
		assertThat(board.past()).isFalse();
	}

	@Test
	void 종료_시각이_지난_수업은_진행_중이_아니다() {
		lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.kim, null, LocalTime.of(13, 50), Set.of(d.hong)));

		PeriodBoard board = service.board(at(2, LocalTime.of(14, 0)));

		assertThat(board.instructor(d.kim.getId()).busy()).isFalse();
		assertThat(board.firstFreeRoomId()).isEqualTo(d.rooms.get(0).getId());
		// 수정 중인 수업 자신은 사용 중으로 세지 않음
		Lesson other = lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.lee, null, null, Set.of(d.hong)));
		assertThat(service.board(at(2, LocalTime.of(14, 0)), other.getId()).firstFreeRoomId())
			.isEqualTo(d.rooms.get(0).getId());
	}

	@Test
	void 조퇴한_교시에는_조퇴로_보이고_다음_교시에서_빠진다() {
		dayStatusRepository.save(new InstructorDayStatus(d.lee, MON, DayStatus.LEAVE_EARLY, 2, LocalDateTime.now()));
		dayStatusRepository.save(new InstructorDayStatus(d.kim, MON, DayStatus.LEAVE_EARLY, 1, LocalDateTime.now()));

		PeriodBoard board = service.board(at(2, LocalTime.of(14, 0)));

		assertThat(board.instructor(d.kim.getId())).isNull();
		InstructorLine lee = board.instructor(d.lee.getId());
		assertThat(lee.leftEarly()).isTrue();
		assertThat(lee.free()).isFalse();
	}

	@Test
	void 기준_시점_판정() {
		LocalDateTime now = FixedClockConfig.NOW;

		assertThat(RefPoint.resolve(now, null, null, null)).isEqualTo(new RefPoint(MON, 2, LocalTime.of(14, 0), true));
		// 지금 교시를 고르면 지금 시각, 다른 교시·다른 날짜면 그 교시 시작 시각
		assertThat(RefPoint.resolve(now, MON, 2, null).time()).isEqualTo(LocalTime.of(14, 0));
		assertThat(RefPoint.resolve(now, MON, 3, null)).isEqualTo(new RefPoint(MON, 3, LocalTime.of(15, 30), false));
		assertThat(RefPoint.resolve(now, LocalDate.of(2026, 11, 3), null, null).time()).isEqualTo(LocalTime.of(13, 0));
		// 시각을 지정하면 그 시각이 속한 교시 (쉬는 시간은 다음 교시)
		assertThat(RefPoint.resolve(now, MON, 1, LocalTime.of(18, 20)).period()).isEqualTo(4);

		assertThat(RefPoint.isPast(MON, 1, now)).isTrue();
		assertThat(RefPoint.isPast(MON, 2, now)).isFalse();
		assertThat(RefPoint.isPast(MON.minusDays(1), 4, now)).isTrue();
	}
}
