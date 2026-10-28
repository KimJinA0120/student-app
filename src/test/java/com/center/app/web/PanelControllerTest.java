package com.center.app.web;

import static com.center.app.lesson.LessonTestData.MON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// 지금 = 11/2(월) 14:00, 2교시. 김강사(수업 중)·이강사 근무
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@Transactional
class PanelControllerTest {

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
		ongoing = lessonRepository.save(new Lesson(MON, 2, d.rooms.get(0), d.kim, null, null, Set.of(d.park)));
	}

	@Test
	void 모든_화면_위에_지금_기준_근무_강사가_보인다() throws Exception {
		mvc.perform(get("/students"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("11/2 (월) 2교시")))
			.andExpect(content().string(containsString("지금 14:00")))
			.andExpect(content().string(containsString("확정 2명")))
			// 수업 중: 강의실·학생 수 + [추가] [종료] [취소]
			.andExpect(content().string(containsString("수업 중 · 203호 · 1명")))
			.andExpect(content().string(containsString("/lessons/" + ongoing.getId() + "/students?back=/students")))
			.andExpect(content().string(containsString("/lessons/" + ongoing.getId() + "/end")))
			// 그 외: [수업시작] → 강사·날짜·교시가 채워진 폼
			.andExpect(content().string(containsString(
				"/lessons/new?instructorId=" + d.lee.getId() + "&amp;date=2026-11-02&amp;period=2&amp;back=/students")));
	}

	@Test
	void 기준_시점을_교시나_시각으로_바꾸고_화면_조건은_유지한다() throws Exception {
		mvc.perform(get("/students").param("q", "홍").param("refPeriod", "3"))
			.andExpect(content().string(containsString("기준 15:30")))
			.andExpect(content().string(containsString("이 교시에 근무하는 강사가 없습니다.")))
			.andExpect(content().string(containsString("<input type=\"hidden\" name=\"q\" value=\"홍\">")))
			.andExpect(content().string(containsString("href=\"/students?q=%ED%99%8D\">지금</a>")));
		mvc.perform(get("/students").param("refTime", "13:10"))
			.andExpect(content().string(containsString("기준 13:10")))
			.andExpect(content().string(containsString("수업 중 · 203호")));
		// 15:00 기준이면 13:00~14:30에 끝난 수업은 진행 중이 아님 (교시 끝나면 자동 종료와 같은 규칙)
		ongoing.end(java.time.LocalTime.of(14, 30));
		mvc.perform(get("/students").param("refTime", "15:00"))
			.andExpect(content().string(not(containsString("수업 중 · 203호"))));
		// 잘못된 값은 무시
		mvc.perform(get("/students").param("refDate", "x").param("refPeriod", "9"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("지금 14:00")));
	}

	@Test
	void 휴식_외출_조퇴와_복귀() throws Exception {
		String base = "/instructors/" + d.lee.getId() + "/day-status";
		mvc.perform(post(base).param("status", "BREAK").param("date", "2026-11-02").param("period", "2")
				.param("back", "/students"))
			.andExpect(redirectedUrl("/students"))
			.andExpect(flash().attribute("message", "휴식(으)로 바꿨습니다."));
		mvc.perform(get("/students"))
			.andExpect(content().string(containsString("/day-status/return")))
			.andExpect(content().string(containsString(">휴식</span>")));

		mvc.perform(post(base + "/return").param("date", "2026-11-02").param("back", "/students"))
			.andExpect(flash().attribute("message", "복귀했습니다."));

		mvc.perform(post(base).param("status", "LEAVE_EARLY").param("date", "2026-11-02").param("period", "2"))
			.andExpect(redirectedUrl("/instructors"));
		mvc.perform(get("/students"))
			.andExpect(content().string(containsString(">조퇴</span>")));
		mvc.perform(post(base + "/return").param("date", "2026-11-02"))
			.andExpect(flash().attribute("error", "조퇴한 강사는 복귀할 수 없습니다."));
		mvc.perform(post(base).param("status", "BREAK").param("date", "2026-11-02").param("period", "2"))
			.andExpect(flash().attribute("error", containsString("이미 조퇴 처리되었습니다")));
	}
}
