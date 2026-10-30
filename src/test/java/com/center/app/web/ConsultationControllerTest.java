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
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.center.app.consultation.Consultation;
import com.center.app.consultation.ConsultationRepository;
import com.center.app.lesson.LessonRepository;
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
class ConsultationControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ConsultationRepository consultationRepository;

	@Autowired
	StudentRepository studentRepository;

	@Autowired
	LessonRepository lessonRepository;

	Consultation lee;

	@BeforeEach
	void setUp() {
		consultationRepository.deleteAll();
		lessonRepository.deleteAll();
		studentRepository.deleteAll();
		lee = consultationRepository.save(new Consultation(new Consultation.Info(
			"이은숙", "010-2778-2454", "AI 기초, 키오스크", "매일", "13:00", "(방문) 기존 수강생 소개 방문 상담",
			LocalDate.of(2026, 10, 29))));
	}

	@Test
	void 목록과_검색() throws Exception {
		mvc.perform(get("/consultations"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이은숙")))
			.andExpect(content().string(containsString("10/29")))
			.andExpect(content().string(containsString("/students/new?consultationId=" + lee.getId())));
		mvc.perform(get("/consultations").param("q", "없는이름"))
			.andExpect(content().string(containsString("조건에 맞는 상담이 없습니다.")));
	}

	@Test
	void 등록_수정_삭제() throws Exception {
		mvc.perform(get("/consultations/new")).andExpect(status().isOk());
		mvc.perform(post("/consultations").param("phone", "010-2959-3032").param("memo", "(전화) 부재중"))
			.andExpect(redirectedUrl("/consultations"))
			.andExpect(flash().attribute("message", "상담을 등록했습니다."));

		// 오류면 입력값을 유지한 채 폼을 다시 보여줌
		mvc.perform(post("/consultations").param("hopeClass", "카톡"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("성명이나 연락처 중 하나는 입력하세요.")))
			.andExpect(content().string(containsString("value=\"카톡\"")));

		mvc.perform(get("/consultations/" + lee.getId() + "/edit"))
			.andExpect(content().string(containsString("value=\"이은숙\"")));
		mvc.perform(post("/consultations/" + lee.getId()).param("name", "이은숙").param("hopeTime", "15:30"))
			.andExpect(redirectedUrl("/consultations"));
		assertThat(consultationRepository.findById(lee.getId()).orElseThrow().getHopeTime()).isEqualTo("15:30");

		mvc.perform(post("/consultations/" + lee.getId() + "/delete"))
			.andExpect(redirectedUrl("/consultations"));
		assertThat(consultationRepository.findById(lee.getId())).isEmpty();
	}

	@Test
	void 수강생_등록_폼에_상담_내용이_채워지고_저장하면_등록됨으로_바뀐다() throws Exception {
		mvc.perform(get("/students/new").param("consultationId", String.valueOf(lee.getId())))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"이은숙\"")))
			.andExpect(content().string(containsString("value=\"010-2778-2454\"")))
			.andExpect(content().string(containsString("value=\"AI 기초, 키오스크\"")))
			.andExpect(content().string(containsString("[상담 10/29] 희망: 매일 13:00\n(방문) 기존 수강생 소개 방문 상담")))
			.andExpect(content().string(containsString("name=\"consultationId\" value=\"" + lee.getId() + "\"")));

		mvc.perform(post("/students").param("name", "이은숙").param("consultationId", String.valueOf(lee.getId())))
			.andExpect(redirectedUrlPattern("/students/*"));
		Student student = studentRepository.findAll().getFirst();

		mvc.perform(get("/consultations"))
			.andExpect(content().string(containsString("등록됨 · 이은숙")))
			.andExpect(content().string(containsString("/students/" + student.getId())))
			.andExpect(content().string(not(containsString("consultationId=" + lee.getId()))));
		mvc.perform(get("/consultations").param("pending", "true"))
			.andExpect(content().string(not(containsString("이은숙"))));

		// 이미 등록한 상담으로 다시 들어오면 목록으로 돌려보냄
		mvc.perform(get("/students/new").param("consultationId", String.valueOf(lee.getId())))
			.andExpect(redirectedUrl("/consultations"))
			.andExpect(flash().attribute("error", "이미 수강생으로 등록한 상담입니다."));
	}
}
