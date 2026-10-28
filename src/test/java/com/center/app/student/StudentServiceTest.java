package com.center.app.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.instructor.InstructorService;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.room.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StudentServiceTest {

	static final LocalDate MON = LocalDate.of(2026, 11, 2);

	@Autowired
	StudentService service;

	@Autowired
	StudentRepository studentRepository;

	@Autowired
	InstructorRepository instructorRepository;

	@Autowired
	InstructorService instructorService;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	RoomRepository roomRepository;

	@Autowired
	EntityManager em;

	Instructor kim;
	Instructor lee;

	@BeforeEach
	void setUp() {
		lessonRepository.deleteAll();
		studentRepository.deleteAll();
		instructorRepository.deleteAll();
		kim = instructorRepository.save(new Instructor("김강사", null));
		lee = instructorRepository.save(new Instructor("이강사", null));
	}

	static Student.Info info(String name) {
		return new Student.Info(name, " 010-1234-5678 ", 70, "스마트폰 기초", "", MON, null);
	}

	@Test
	void 등록할_때_공백을_정리하고_희망_강사를_연결한다() {
		Long id = service.create(info(" 홍길동 "), Set.of(kim.getId(), lee.getId()));
		em.flush();
		em.clear();

		Student s = service.get(id);
		assertThat(s.getName()).isEqualTo("홍길동");
		assertThat(s.getPhone()).isEqualTo("010-1234-5678");
		assertThat(s.getAttendId()).isNull();
		assertThat(s.getHopeInstructors()).extracting(Instructor::getName).containsExactlyInAnyOrder("김강사", "이강사");
	}

	@Test
	void 입력_오류는_한국어_메시지로_알린다() {
		assertThatThrownBy(() -> service.create(info(" "), Set.of())).hasMessage("성명을(를) 입력하세요.");
		assertThatThrownBy(() -> service.create(new Student.Info("홍", null, 200, null, null, null, null), Set.of()))
			.hasMessage("나이를 확인하세요.");
		assertThatThrownBy(() -> service.create(info("홍"), Set.of(-1L))).hasMessageContaining("희망 강사를 찾을 수 없습니다");
	}

	@Test
	void 목록은_이름_검색과_방문_회차를_계산한다() {
		Long hong = service.create(info("홍길동"), Set.of(kim.getId()));
		service.create(info("박영희"), Set.of());
		Student student = studentRepository.findById(hong).orElseThrow();
		var room = roomRepository.findAllByOrderBySortOrderAsc().getFirst();
		lessonRepository.save(new Lesson(MON, 1, room, kim, null, null, Set.of(student)));
		lessonRepository.save(new Lesson(MON.plusDays(1), 2, room, lee, null, null, Set.of(student)));
		em.flush();
		em.clear();

		assertThat(service.list("")).extracting(StudentRow::name).containsExactly("박영희", "홍길동");
		StudentRow row = service.list("길").getFirst();
		assertThat(row.visitCount()).isEqualTo(2);
		assertThat(row.hopeInstructorNames()).containsExactly("김강사");
		// 상세 수업 목록은 최근순
		assertThat(service.lessonsOf(hong)).extracting(StudentService.LessonLine::instructorName)
			.containsExactly("이강사", "김강사");
	}

	@Test
	void 수업_기록이_있는_수강생과_강사는_삭제할_수_없다() {
		Long hong = service.create(info("홍길동"), Set.of(kim.getId()));
		Long park = service.create(info("박영희"), Set.of(lee.getId()));
		var room = roomRepository.findAllByOrderBySortOrderAsc().getFirst();
		lessonRepository.save(new Lesson(MON, 1, room, kim, null, null,
			Set.of(studentRepository.findById(hong).orElseThrow())));
		em.flush();

		assertThatThrownBy(() -> service.delete(hong)).hasMessageContaining("수업 기록이 있는 수강생");
		assertThatThrownBy(() -> instructorService.delete(kim.getId())).hasMessageContaining("수업 기록이 있는 강사");

		// 수업 기록이 없으면 삭제 가능. 희망 강사로 연결된 강사도 연결만 지우고 삭제됨
		service.delete(park);
		Long other = service.create(info("최철수"), Set.of(lee.getId()));
		em.flush();
		instructorService.delete(lee.getId());
		em.flush();
		em.clear();
		assertThat(service.get(other).getHopeInstructors()).isEmpty();
	}
}
