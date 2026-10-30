package com.center.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorOverride;
import com.center.app.instructor.InstructorOverrideRepository;
import com.center.app.instructor.InstructorRepository;
import com.center.app.instructor.InstructorRole;
import com.center.app.instructor.OverrideType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 템플릿까지 실제로 렌더링해서 화면 오류(잘못된 표현식 등)를 잡는 테스트
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstructorControllerTest {

	// 2026-11-02 월요일
	static final LocalDate MON = LocalDate.of(2026, 11, 2);

	@Autowired
	MockMvc mvc;

	@Autowired
	InstructorRepository instructorRepository;

	@Autowired
	InstructorOverrideRepository overrideRepository;

	Instructor kim;

	@BeforeEach
	void setUp() {
		instructorRepository.deleteAll();
		kim = new Instructor("김강사", "010-1111-1111");
		kim.addSlot(DayOfWeek.MONDAY, 1, InstructorRole.MAIN, "오전 담당");
		Instructor lee = new Instructor("이강사", null);
		instructorRepository.save(kim);
		instructorRepository.save(lee);
		overrideRepository.save(new InstructorOverride(lee, OverrideType.CONDITIONAL, MON, MON, Set.of(), null, "확인 중"));
		overrideRepository.save(new InstructorOverride(kim, OverrideType.NOTE, MON, MON, Set.of(), null, "1시간 늦음"));
	}

	@Test
	void 날짜별_보기() throws Exception {
		mvc.perform(get("/instructors").param("date", "2026-11-02"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("11월 2일 월요일")))
			.andExpect(content().string(containsString("김강사")))
			// 연락처·기본 배치 비고(주황 글씨)는 시간표에서 빼고 강사 상세에서 봄
			.andExpect(content().string(not(containsString("010-1111-1111"))))
			.andExpect(content().string(not(containsString("오전 담당"))))
			// 총 = 고정 1(김) + 조건부 1(이)
			.andExpect(content().string(containsString("총 2명 · 부족 6")))
			.andExpect(content().string(containsString("고정 1 · 조건부 1 · 결근 0")))
			.andExpect(content().string(containsString("1시간 늦음")))
			.andExpect(content().string(containsString("class=\"cond\">조<")));
	}

	@Test
	void 날짜가_없으면_오늘로_열린다() throws Exception {
		mvc.perform(get("/instructors")).andExpect(status().isOk());
	}

	@Test
	void 이름_검색() throws Exception {
		mvc.perform(get("/instructors/search").param("q", "김"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("김강사")))
			.andExpect(content().string(containsString("11/2 (월)")));
		mvc.perform(get("/instructors/search").param("q", "없는이름"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이름을 찾을 수 없습니다")));
		mvc.perform(get("/instructors/search").param("q", " "))
			.andExpect(redirectedUrl("/instructors"));
	}

	@Test
	void 전체_달력() throws Exception {
		mvc.perform(get("/instructors/calendar"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10/27")))
			.andExpect(content().string(containsString("12/10")))
			.andExpect(content().string(containsString("href=\"/instructors?date=2026-11-02\"")));
	}

	@Test
	void 강사_관리_목록과_상세() throws Exception {
		mvc.perform(get("/instructors/manage"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("김강사")));
		mvc.perform(get("/instructors/" + kim.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"role_MONDAY_1\"")))
			.andExpect(content().string(containsString("value=\"오전 담당\"")))
			.andExpect(content().string(containsString("1시간 늦음")));
	}

	@Test
	void 등록하면_상세로_이동하고_오류면_메시지와_함께_돌아간다() throws Exception {
		mvc.perform(post("/instructors").param("name", "새강사").param("phone", "010-2222-2222"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", containsString("등록했습니다")));
		mvc.perform(post("/instructors").param("name", " "))
			.andExpect(redirectedUrl("/instructors/manage"))
			.andExpect(flash().attribute("error", "이름을 입력하세요."));
	}

	@Test
	void 기본_배치와_예외를_저장한다() throws Exception {
		String base = "/instructors/" + kim.getId();
		mvc.perform(post(base + "/slots").param("role_TUESDAY_2", "ASSIST").param("note_TUESDAY_2", "격주"))
			.andExpect(redirectedUrl(base))
			.andExpect(flash().attribute("message", "기본 배치를 저장했습니다."));
		mvc.perform(post(base + "/overrides").param("type", "ABSENT").param("startDate", "2026-11-09")
				.param("endDate", "").param("periods", "3").param("role", "").param("memo", "병원"))
			.andExpect(redirectedUrl(base))
			.andExpect(flash().attribute("message", "예외를 추가했습니다."));
		mvc.perform(post(base + "/overrides").param("type", "ABSENT").param("startDate", "잘못된날짜"))
			.andExpect(flash().attribute("error", containsString("날짜")));

		mvc.perform(get(base))
			.andExpect(content().string(containsString("value=\"격주\"")))
			.andExpect(content().string(containsString("11/9 (월)")))
			.andExpect(content().string(containsString("병원")));
	}
}
