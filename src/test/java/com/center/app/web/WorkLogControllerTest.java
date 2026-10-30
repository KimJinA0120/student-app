package com.center.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;

import com.center.app.common.FixedClockConfig;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.lesson.LessonTestData;
import com.center.app.worklog.WorkLog;
import com.center.app.worklog.WorkLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FixedClockConfig.class)
class WorkLogControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	WorkLogRepository repository;

	@Autowired
	LessonRepository lessonRepository;

	@Autowired
	LessonTestData.Factory factory;

	WorkLog oct29;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		LessonTestData d = factory.create();
		// 오늘(11/2) 2교시 수업 하나, 박영희는 첫 수업
		lessonRepository.save(new Lesson(LessonTestData.MON, 2, d.rooms.getFirst(), d.kim, null, null, Set.of(d.park)));
		oct29 = repository.save(new WorkLog(new WorkLog.Info(LocalDate.of(2026, 10, 29), 4, 6, 3, 3, 5, 16,
			"- 1교시 강사 출석 완료", "- 205호 wifi 안됨")));
	}

	@Test
	void 목록에_누적_합계와_카톡_글이_있다() throws Exception {
		mvc.perform(get("/work-logs"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10/29(목)")))
			.andExpect(content().string(containsString("누적 강좌 <b>16</b>")))
			.andExpect(content().string(containsString("data-copy=\"kakao-" + oct29.getId() + "\"")))
			.andExpect(content().string(containsString("■ 수업 강좌 16개")));
	}

	@Test
	void 새로_쓸_때_오늘_수업_기록으로_미리_채운다() throws Exception {
		mvc.perform(get("/work-logs/new"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"2026-11-02\"")))
			.andExpect(content().string(containsString("name=\"period2\" value=\"1\"")))
			.andExpect(content().string(containsString("name=\"newCount\" value=\"1\"")));

		// 이미 쓴 날짜를 고르면 수정 화면으로
		mvc.perform(get("/work-logs/new").param("date", "2026-10-29"))
			.andExpect(redirectedUrl("/work-logs/" + oct29.getId() + "/edit"))
			.andExpect(flash().attribute("warning", "이미 작성한 업무일지입니다. 여기서 수정하세요."));
	}

	@Test
	void 작성_수정_삭제() throws Exception {
		mvc.perform(post("/work-logs").param("logDate", "2026-11-02").param("period2", "1")
				.param("specialNote", "- 전등 수리 완료"))
			.andExpect(redirectedUrl("/work-logs"))
			.andExpect(flash().attribute("message", "업무일지를 저장했습니다."));

		// 같은 날짜는 막고 입력값을 유지한 채 폼을 다시 보여줌
		mvc.perform(post("/work-logs").param("logDate", "2026-11-02").param("specialNote", "두 번째"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("11/2 업무일지가 이미 있습니다.")))
			.andExpect(content().string(containsString("두 번째")));

		mvc.perform(get("/work-logs/" + oct29.getId() + "/edit"))
			.andExpect(content().string(containsString("카톡 복사")))
			.andExpect(content().string(containsString("- 205호 wifi 안됨")));
		mvc.perform(post("/work-logs/" + oct29.getId()).param("logDate", "2026-10-29").param("period4", "2"))
			.andExpect(redirectedUrl("/work-logs"));
		assertThat(repository.findById(oct29.getId()).orElseThrow().getPeriod4()).isEqualTo(2);

		mvc.perform(post("/work-logs/" + oct29.getId() + "/delete"))
			.andExpect(redirectedUrl("/work-logs"));
		assertThat(repository.findById(oct29.getId())).isEmpty();
	}
}
