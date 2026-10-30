package com.center.app.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import com.center.app.lesson.LessonRepository;
import com.center.app.student.Student;
import com.center.app.student.StudentRepository;
import com.center.app.student.StudentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ConsultationServiceTest {

	@Autowired
	ConsultationService service;

	@Autowired
	ConsultationRepository repository;

	@Autowired
	StudentService studentService;

	@Autowired
	StudentRepository studentRepository;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	EntityManager em;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		lessonRepository.deleteAll();
		studentRepository.deleteAll();
	}

	static Consultation.Info info(String name, String phone, LocalDate date) {
		return new Consultation.Info(name, phone, "스마트폰 기초", "매일", "13:00", "(방문) 기존 수강생 소개", date);
	}

	@Test
	void 등록할_때_공백을_정리하고_성명이나_연락처_하나는_받는다() {
		Long id = service.create(new Consultation.Info(" ", " 010-2959-3032 ", "", null, null, null, null));
		Consultation c = service.get(id);
		assertThat(c.getName()).isNull();
		assertThat(c.getPhone()).isEqualTo("010-2959-3032");
		assertThat(c.getHopeClass()).isNull();

		assertThatThrownBy(() -> service.create(new Consultation.Info(" ", "", "스마트폰 기초", null, null, null, null)))
			.hasMessage("성명이나 연락처 중 하나는 입력하세요.");
	}

	@Test
	void 목록은_최근_상담일순이고_이름_연락처로_찾는다() {
		service.create(info("이길용", "010-3322-6889", LocalDate.of(2026, 10, 24)));
		service.create(info("이추경", "010-2725-7464", LocalDate.of(2026, 10, 30)));
		service.create(info("함봉자", "010-2849-3443", null));

		assertThat(service.list("", false)).extracting(Consultation::getName)
			.containsExactly("이추경", "이길용", "함봉자");
		assertThat(service.list(" 이 ", false)).extracting(Consultation::getName).containsExactly("이추경", "이길용");
		assertThat(service.list("3443", false)).extracting(Consultation::getName).containsExactly("함봉자");
	}

	@Test
	void 수강생으로_등록하면_연결되고_두_번_등록은_막는다() {
		Long id = service.create(info("이길용", "010-3322-6889", LocalDate.of(2026, 10, 24)));
		Student.Info student = new Student.Info("이길용", "010-3322-6889", null, "스마트폰 기초", null, null, null);
		Long studentId = studentService.create(student, Set.of(), id);
		em.flush();
		em.clear();

		assertThat(service.get(id).getStudent().getId()).isEqualTo(studentId);
		assertThat(service.list("", true)).isEmpty();
		assertThatThrownBy(() -> service.getForRegistration(id)).hasMessage("이미 수강생으로 등록한 상담입니다.");
		assertThatThrownBy(() -> studentService.create(student, Set.of(), id))
			.hasMessage("이미 수강생으로 등록한 상담입니다.");
	}

	@Test
	void 수강생을_지우면_상담_기록은_남고_연결만_끊긴다() {
		Long id = service.create(info("이길용", "010-3322-6889", null));
		Long studentId = studentService.create(
			new Student.Info("이길용", null, null, null, null, null, null), Set.of(), id);
		em.flush();
		em.clear();

		studentService.delete(studentId);
		em.flush();
		em.clear();

		assertThat(service.get(id).getStudent()).isNull();
		assertThat(service.list("", true)).extracting(Consultation::getName).containsExactly("이길용");
	}
}
