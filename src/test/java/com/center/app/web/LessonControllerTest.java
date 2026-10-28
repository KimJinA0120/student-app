package com.center.app.web;

import static com.center.app.lesson.LessonTestData.MON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.Set;

import com.center.app.common.FixedClockConfig;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.lesson.LessonTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 지금 = 11/2(월) 14:00, 2교시. 김강사·이강사가 월 2교시 근무
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@Transactional
class LessonControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	LessonTestData.Factory factory;

	@Autowired
	LessonRepository lessonRepository;

	LessonTestData d;
	Lesson ongoing;

	@BeforeEach
	void setUp() {
		d = factory.create();
		// 김강사가 203호에서 박영희와 수업 중
		ongoing = lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.kim, null, null, Set.of(d.park)));
	}

	String option(Long id) {
		return "<option value=\"" + id + "\"";
	}

	@Test
	void 빈_폼은_지금_날짜_교시와_빈_교실이_자동으로_채워진다() throws Exception {
		mvc.perform(get("/lessons/new"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"2026-11-02\"")))
			.andExpect(content().string(containsString("<option value=\"2\" selected=\"selected\">2교시")))
			// 203호는 사용 중이라 선택 불가, 204호 자동 선택
			.andExpect(content().string(containsString(option(d.rooms.get(0).getId()) + " disabled=\"disabled\">203호 (사용 중 · 김강사)")))
			.andExpect(content().string(containsString(option(d.rooms.get(1).getId()) + " selected=\"selected\">204호")))
			// 근무 중인 강사만, 수업 중인 김강사는 선택 불가
			.andExpect(content().string(containsString("김강사 · 수업 중 203호")))
			.andExpect(content().string(containsString("data-free=\"true\" data-name=\"이강사\"")))
			.andExpect(content().string(not(containsString("최강사"))));
	}

	@Test
	void 수강생이나_강사가_미리_채워진_폼() throws Exception {
		// 수강생 목록 체크 / 수강생 상세 → 수강생, 패널 [수업시작] → 강사
		mvc.perform(get("/lessons/new").param("studentIds", d.hong.getId().toString())
				.param("instructorId", d.lee.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(matchesPattern("(?s).*value=\"" + d.hong.getId() + "\"[^>]*checked=\"checked\".*")))
			.andExpect(content().string(containsString("data-hopes=\"" + d.lee.getId() + "\"")))
			.andExpect(content().string(matchesPattern("(?s).*" + option(d.lee.getId()) + "[^>]*selected=\"selected\".*")));
	}

	@Test
	void 지난_날짜는_전체_강사에서_고른다() throws Exception {
		mvc.perform(get("/lessons/new").param("date", "2026-10-30").param("period", "1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("지난 기록")))
			.andExpect(content().string(containsString("최강사")));
		// 형식이 틀린 날짜는 무시하고 오늘로
		mvc.perform(get("/lessons/new").param("date", "잘못"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"2026-11-02\"")));
	}

	@Test
	void 저장하면_돌아갈_화면으로_이동하고_오류면_입력값을_유지한다() throws Exception {
		mvc.perform(post("/lessons").param("studentIds", d.hong.getId().toString())
				.param("instructorId", d.lee.getId().toString()).param("roomId", d.rooms.get(1).getId().toString())
				.param("date", "2026-11-02").param("period", "2").param("startTime", "14:00").param("endTime", "")
				.param("back", "/students"))
			.andExpect(redirectedUrl("/students"))
			.andExpect(flash().attribute("message", "수업을 기록했습니다."));

		mvc.perform(post("/lessons").param("studentIds", d.hong.getId().toString())
				.param("instructorId", d.kim.getId().toString()).param("roomId", d.rooms.get(2).getId().toString())
				.param("date", "2026-11-02").param("period", "2").param("back", "//evil.example"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("김강사 강사님은 같은 시간에 203호에서 수업 중입니다.")))
			.andExpect(content().string(matchesPattern("(?s).*value=\"" + d.hong.getId() + "\"[^>]*checked=\"checked\".*")));

		// 지난 기록은 겹쳐도 저장 + 경고. 외부 주소로는 돌아가지 않음
		lessonRepository.save(new Lesson(MON, 1, d.rooms.get(0), d.kim, null, null, Set.of(d.park)));
		mvc.perform(post("/lessons").param("studentIds", d.hong.getId().toString())
				.param("instructorId", d.kim.getId().toString()).param("roomId", d.rooms.get(0).getId().toString())
				.param("date", "2026-11-02").param("period", "1").param("back", "//evil.example"))
			.andExpect(redirectedUrl("/lessons"))
			.andExpect(flash().attribute("warning", containsString("저장했지만 확인하세요")));
	}

	@Test
	void 수업_기록_탭_목록과_검색() throws Exception {
		lessonRepository.save(new Lesson(MON, 3, d.rooms.get(1), d.lee, LocalTime.of(15, 40), LocalTime.of(17, 0),
			Set.of(d.hong)));
		mvc.perform(get("/lessons"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("2건")))
			.andExpect(content().string(containsString("15:40~17:00")))
			.andExpect(content().string(containsString("/lessons/" + ongoing.getId() + "/edit")));
		mvc.perform(get("/lessons").param("q", "홍길"))
			.andExpect(content().string(containsString("1건")))
			.andExpect(content().string(containsString("이강사")));
	}

	@Test
	void 수정_폼과_삭제() throws Exception {
		mvc.perform(get("/lessons/" + ongoing.getId() + "/edit"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("수업 기록 수정")))
			// 자기 자신은 사용 중으로 세지 않음 → 203호·김강사 선택 가능
			.andExpect(content().string(containsString(option(d.rooms.get(0).getId()) + " selected=\"selected\">203호<")))
			.andExpect(content().string(containsString("수업 기록 삭제")));
		mvc.perform(post("/lessons/" + ongoing.getId() + "/delete").param("back", "/instructors"))
			.andExpect(redirectedUrl("/instructors"))
			.andExpect(flash().attribute("message", "수업 기록을 삭제했습니다."));
	}

	@Test
	void 종료와_수강생_추가() throws Exception {
		mvc.perform(get("/lessons/" + ongoing.getId() + "/students").param("back", "/students"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("지금 수강생: 박영희")))
			.andExpect(content().string(containsString("홍길동")));
		mvc.perform(post("/lessons/" + ongoing.getId() + "/students").param("studentIds", d.hong.getId().toString())
				.param("back", "/students"))
			.andExpect(redirectedUrl("/students"))
			.andExpect(flash().attribute("message", "수강생을 추가했습니다."));
		mvc.perform(post("/lessons/" + ongoing.getId() + "/students").param("back", "/students"))
			.andExpect(flash().attribute("error", "추가할 수강생을 선택하세요."));

		mvc.perform(post("/lessons/" + ongoing.getId() + "/end").param("back", "/students"))
			.andExpect(redirectedUrl("/students"))
			.andExpect(flash().attribute("message", "수업을 종료했습니다."));
		mvc.perform(post("/lessons/" + ongoing.getId() + "/end"))
			.andExpect(redirectedUrl("/lessons"))
			.andExpect(flash().attribute("error", containsString("이미 끝난 수업")));
	}
}
