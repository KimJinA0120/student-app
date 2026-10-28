package com.center.app.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.center.app.manager.ManagerDay.Working;
import com.center.app.manager.ManagerService.ShiftInput;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ManagerServiceTest {

	static final LocalDate MON = LocalDate.of(2026, 11, 2);

	@Autowired
	ManagerService service;

	@Autowired
	ManagerRepository managerRepository;

	@Autowired
	EntityManager em;

	Long kim;
	Long lee;
	Long park;

	static LocalTime t(int h) {
		return LocalTime.of(h, 0);
	}

	@BeforeEach
	void setUp() {
		managerRepository.deleteAll();
		kim = service.create(" 김매니저 ", "");
		lee = service.create("이매니저", null);
		park = service.create("박매니저", null);
		service.replaceShifts(kim, List.of(new ShiftInput(DayOfWeek.MONDAY, t(9), t(18))));
		service.replaceShifts(lee, List.of(new ShiftInput(DayOfWeek.MONDAY, t(13), t(21))));
		service.replaceShifts(park, List.of(new ShiftInput(DayOfWeek.MONDAY, t(9), t(18))));
	}

	@Test
	void 날짜별_근무는_기본_근무에서_휴무를_빼고_시간_변경을_반영한다() {
		service.addException(park, MON, ManagerExceptionType.DAY_OFF, null, null, "연차");
		service.addException(lee, MON, ManagerExceptionType.TIME_CHANGE, t(10), t(15), null);
		em.flush();
		em.clear();

		ManagerDay day = service.day(MON);
		assertThat(day.working()).extracting(Working::name, Working::startTime, Working::changed)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("김매니저", t(9), false),
				org.assertj.core.groups.Tuple.tuple("이매니저", t(10), true));
		assertThat(day.changes()).extracting(ManagerDay.Change::name).containsExactly("박매니저", "이매니저");
		// 화요일은 기본 근무 없음
		assertThat(service.day(MON.plusDays(1)).working()).isEmpty();
	}

	@Test
	void 기본_근무표를_같은_요일_포함해서_다시_저장해도_유니크_제약에_걸리지_않는다() {
		service.replaceShifts(kim, List.of(
			new ShiftInput(DayOfWeek.MONDAY, t(10), t(19)),
			new ShiftInput(DayOfWeek.TUESDAY, t(9), t(18))));
		em.flush();
		em.clear();

		Manager m = service.get(kim);
		assertThat(m.getName()).isEqualTo("김매니저");
		assertThat(m.getPhone()).isNull();
		assertThat(m.getShifts()).hasSize(2);
		assertThat(m.shiftOn(DayOfWeek.MONDAY).getStartTime()).isEqualTo(t(10));
	}

	@Test
	void 입력_검증() {
		assertThatThrownBy(() -> service.create(" ", null)).hasMessage("이름을(를) 입력하세요.");
		assertThatThrownBy(() -> service.replaceShifts(kim, List.of(new ShiftInput(DayOfWeek.MONDAY, t(18), t(9)))))
			.hasMessage("월요일 종료 시각은 시작 시각보다 늦어야 합니다.");
		assertThatThrownBy(() -> service.replaceShifts(kim, List.of(new ShiftInput(DayOfWeek.FRIDAY, t(9), null))))
			.hasMessage("금요일 시작·종료 시각을 모두 입력하세요.");
		assertThatThrownBy(() -> service.addException(kim, MON, ManagerExceptionType.TIME_CHANGE, null, null, null))
			.hasMessage("시간 변경은 시작·종료 시각을 입력하세요.");
		service.addException(kim, MON, ManagerExceptionType.DAY_OFF, t(9), t(10), null);
		assertThatThrownBy(() -> service.addException(kim, MON, ManagerExceptionType.DAY_OFF, null, null, null))
			.hasMessageContaining("이미 예외가 있습니다");
		// 휴무에는 시각을 저장하지 않음
		assertThat(service.exceptionsOf(kim).getFirst().getStartTime()).isNull();
	}

	@Test
	void 예외_삭제와_매니저_삭제() {
		service.addException(kim, MON, ManagerExceptionType.DAY_OFF, null, null, null);
		em.flush();
		Long exId = service.exceptionsOf(kim).getFirst().getId();
		assertThatThrownBy(() -> service.deleteException(lee, exId)).hasMessageContaining("예외를 찾을 수 없습니다");
		service.deleteException(kim, exId);

		service.addException(kim, MON, ManagerExceptionType.DAY_OFF, null, null, null);
		em.flush();
		service.delete(kim);
		em.flush();
		em.clear();
		assertThat(service.findAll()).extracting(Manager::getName).containsExactly("박매니저", "이매니저");
	}
}
