package com.center.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.room.RoomRepository;
import com.center.app.student.Student;
import com.center.app.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	StudentRepository studentRepository;

	@Autowired
	InstructorRepository instructorRepository;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	RoomRepository roomRepository;

	Instructor kim;
	Student hong;

	@BeforeEach
	void setUp() {
		lessonRepository.deleteAll();
		studentRepository.deleteAll();
		instructorRepository.deleteAll();
		kim = instructorRepository.save(new Instructor("김강사", null));
		hong = studentRepository.save(new Student(
			new Student.Info("홍길동", "010-1234-5678", 70, "스마트폰 기초", "hong01", null, "첫 줄\n둘째 줄"),
			Set.of(kim)));
		lessonRepository.save(new Lesson(LocalDate.of(2026, 11, 2), 3,
			roomRepository.findAllByOrderBySortOrderAsc().getFirst(), kim, null, null, Set.of(hong)));
	}

	@Test
	void 목록과_이름_검색() throws Exception {
		mvc.perform(get("/students"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("홍길동")))
			.andExpect(content().string(containsString("1회")))
			.andExpect(content().string(containsString("김강사")))
			.andExpect(content().string(containsString("name=\"studentIds\" form=\"start-form\" value=\"" + hong.getId() + "\"")));
		mvc.perform(get("/students").param("q", "없는이름"))
			.andExpect(content().string(containsString("이름을 찾을 수 없습니다")))
			.andExpect(content().string(not(containsString("홍길동</b>"))));
	}

	@Test
	void 상세는_기본_정보와_방문_회차와_수업_목록을_보여준다() throws Exception {
		mvc.perform(get("/students/" + hong.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("방문 1회")))
			.andExpect(content().string(containsString("hong01")))
			.andExpect(content().string(containsString("11/2 (월)")))
			.andExpect(content().string(containsString("3교시")))
			.andExpect(content().string(containsString("/lessons/new?studentIds=" + hong.getId())));
	}

	@Test
	void 등록_수정_폼과_저장() throws Exception {
		mvc.perform(get("/students/new")).andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"hopeInstructorIds\"")));
		mvc.perform(get("/students/" + hong.getId() + "/edit")).andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"홍길동\"")))
			.andExpect(content().string(containsString("checked")));

		mvc.perform(post("/students").param("name", "박영희").param("age", "65")
				.param("startDate", "2026-11-03").param("hopeInstructorIds", String.valueOf(kim.getId())))
			.andExpect(redirectedUrlPattern("/students/*"))
			.andExpect(flash().attribute("message", "수강생을 등록했습니다."));

		// 오류면 입력값을 유지한 채 폼을 다시 보여줌
		mvc.perform(post("/students").param("name", " ").param("hopeClass", "카톡"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("성명을(를) 입력하세요.")))
			.andExpect(content().string(containsString("value=\"카톡\"")));
		mvc.perform(post("/students/" + hong.getId()).param("name", "홍길동").param("age", "일흔"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("입력 형식이 올바르지 않습니다")));
	}

	@Test
	void 수업_기록이_있으면_삭제를_막는다() throws Exception {
		mvc.perform(post("/students/" + hong.getId() + "/delete"))
			.andExpect(redirectedUrl("/students/" + hong.getId()))
			.andExpect(flash().attribute("error", containsString("수업 기록이 있는 수강생")));
	}
}
