package com.center.app.instructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.center.app.instructor.InstructorService.SlotInput;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InstructorServiceTest {

	static final LocalDate MON = LocalDate.of(2026, 11, 2);

	@Autowired
	InstructorService service;

	@Autowired
	InstructorRepository instructorRepository;

	@Autowired
	EntityManager em;

	Long id;

	@BeforeEach
	void setUp() {
		instructorRepository.deleteAll();
		id = service.create(" 김강사 ", "");
	}

	@Test
	void 등록할_때_공백을_정리하고_빈_연락처는_null로_저장한다() {
		Instructor saved = service.get(id);

		assertThat(saved.getName()).isEqualTo("김강사");
		assertThat(saved.getPhone()).isNull();
		assertThatThrownBy(() -> service.create(" ", null)).hasMessage("이름을 입력하세요.");
	}

	@Test
	void 기본_배치를_같은_칸_포함해서_다시_저장해도_유니크_제약에_걸리지_않는다() {
		service.replaceSlots(id, List.of(
			new SlotInput(DayOfWeek.MONDAY, 1, InstructorRole.MAIN, null),
			new SlotInput(DayOfWeek.MONDAY, 2, InstructorRole.MAIN, null)));
		em.flush();
		em.clear();

		// 월 1교시는 그대로(역할·비고만 변경), 월 2교시는 삭제, 화 3교시는 새로
		service.replaceSlots(id, List.of(
			new SlotInput(DayOfWeek.MONDAY, 1, InstructorRole.ASSIST, " 오전만 "),
			new SlotInput(DayOfWeek.TUESDAY, 3, InstructorRole.MAIN, "")));
		em.flush();
		em.clear();

		assertThat(service.get(id).getSlots())
			.extracting(InstructorSlot::getDayOfWeek, InstructorSlot::getPeriod, InstructorSlot::getRole,
				InstructorSlot::getNote)
			.containsExactlyInAnyOrder(
				tuple(DayOfWeek.MONDAY, 1, InstructorRole.ASSIST, "오전만"),
				tuple(DayOfWeek.TUESDAY, 3, InstructorRole.MAIN, null));
	}

	@Test
	void 예외는_종료일을_비우면_하루짜리이고_추가_배치가_아니면_역할을_버린다() {
		service.addOverride(id, OverrideType.ABSENT, MON, null, Set.of(), InstructorRole.MAIN, " ");

		assertThat(service.overridesOf(id)).singleElement().satisfies(o -> {
			assertThat(o.getEndDate()).isEqualTo(MON);
			assertThat(o.isAllPeriods()).isTrue();
			assertThat(o.getRole()).isNull();
			assertThat(o.getMemo()).isNull();
		});
	}

	@Test
	void 예외_입력_오류는_메시지로_알린다() {
		assertThatThrownBy(() -> service.addOverride(id, OverrideType.ABSENT, MON, MON.minusDays(1), null, null, null))
			.hasMessage("종료일이 시작일보다 빠릅니다.");
		assertThatThrownBy(() -> service.addOverride(id, OverrideType.NOTE, MON, null, null, null, ""))
			.hasMessage("메모 종류는 내용을 입력하세요.");
		assertThatThrownBy(() -> service.addOverride(id, OverrideType.EXTRA, MON, null, Set.of(5), null, null))
			.hasMessage("교시는 1~4만 가능합니다.");
	}

	@Test
	void 다른_강사의_예외는_삭제할_수_없다() {
		Long other = service.create("이강사", null);
		service.addOverride(other, OverrideType.ABSENT, MON, null, null, null, null);
		Long overrideId = service.overridesOf(other).getFirst().getId();

		assertThatThrownBy(() -> service.deleteOverride(id, overrideId)).isInstanceOf(IllegalArgumentException.class);
		service.deleteOverride(other, overrideId);
		assertThat(service.overridesOf(other)).isEmpty();
	}

	@Test
	void 강사를_삭제하면_기본_배치와_예외도_함께_지워진다() {
		service.replaceSlots(id, List.of(new SlotInput(DayOfWeek.MONDAY, 1, InstructorRole.MAIN, null)));
		service.addOverride(id, OverrideType.ABSENT, MON, null, null, null, null);
		em.flush();
		em.clear();

		service.delete(id);
		em.flush();
		em.clear();

		assertThat(instructorRepository.findById(id)).isEmpty();
		assertThat(service.overridesOf(id)).isEmpty();
	}
}
