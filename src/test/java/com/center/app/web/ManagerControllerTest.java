package com.center.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.center.app.manager.ManagerRepository;
import com.center.app.manager.ManagerService;
import com.center.app.manager.ManagerService.ShiftInput;
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
class ManagerControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ManagerService service;

	@Autowired
	ManagerRepository managerRepository;

	Long kim;

	@BeforeEach
	void setUp() {
		managerRepository.deleteAll();
		kim = service.create("김매니저", "010-3333-3333");
		service.replaceShifts(kim, List.of(new ShiftInput(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))));
	}

	@Test
	void 날짜별_보기() throws Exception {
		mvc.perform(get("/managers").param("date", "2026-11-02"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("11월 2일 월요일")))
			.andExpect(content().string(containsString("김매니저")))
			.andExpect(content().string(containsString("09:00~18:00")));
		mvc.perform(get("/managers").param("date", "2026-11-03"))
			.andExpect(content().string(containsString("근무하는 매니저가 없습니다")));
		mvc.perform(get("/managers")).andExpect(status().isOk());
	}

	@Test
	void 관리_목록과_상세() throws Exception {
		mvc.perform(get("/managers/manage"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("월 09:00~18:00")));
		mvc.perform(get("/managers/" + kim))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"start_MONDAY\" value=\"09:00\"")))
			.andExpect(content().string(containsString("name=\"start_TUESDAY\"")));
	}

	@Test
	void 등록_근무표_예외_저장() throws Exception {
		mvc.perform(post("/managers").param("name", "이매니저"))
			.andExpect(redirectedUrlPattern("/managers/*"))
			.andExpect(flash().attribute("message", containsString("등록했습니다")));
		mvc.perform(post("/managers").param("name", ""))
			.andExpect(redirectedUrl("/managers/manage"))
			.andExpect(flash().attribute("error", "이름을(를) 입력하세요."));

		String base = "/managers/" + kim;
		mvc.perform(post(base + "/shifts").param("start_TUESDAY", "10:00").param("end_TUESDAY", "19:00")
				.param("start_MONDAY", "").param("end_MONDAY", ""))
			.andExpect(redirectedUrl(base))
			.andExpect(flash().attribute("message", "기본 근무표를 저장했습니다."));
		mvc.perform(post(base + "/shifts").param("start_FRIDAY", "25:00").param("end_FRIDAY", "19:00"))
			.andExpect(flash().attribute("error", containsString("시각 형식")));

		mvc.perform(post(base + "/exceptions").param("date", "2026-11-03").param("type", "TIME_CHANGE")
				.param("startTime", "13:00").param("endTime", "18:00").param("memo", "오후 출근"))
			.andExpect(flash().attribute("message", "예외를 추가했습니다."));
		mvc.perform(post(base + "/exceptions").param("date", "잘못").param("type", "DAY_OFF"))
			.andExpect(flash().attribute("error", containsString("입력 형식")));

		mvc.perform(get("/managers").param("date", "2026-11-03"))
			.andExpect(content().string(containsString("13:00~18:00")))
			.andExpect(content().string(containsString("시간 변경")))
			.andExpect(content().string(containsString("오후 출근")));
	}
}
