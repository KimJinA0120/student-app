package com.center.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.center.app.checkitem.CheckItemRepository;
import com.center.app.common.FixedClockConfig;
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
@Import(FixedClockConfig.class)
@Transactional
class CheckItemControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	CheckItemRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	@Test
	void 모든_화면의_사이드_패널에서_등록_해결_삭제() throws Exception {
		mvc.perform(post("/check-items").param("content", "11/9 결근 확인").param("personName", "김강사")
				.param("back", "/managers"))
			.andExpect(redirectedUrl("/managers"));
		mvc.perform(post("/check-items").param("content", "지울 항목").param("back", "/managers"));
		mvc.perform(get("/managers"))
			.andExpect(content().string(containsString("확인 필요 (2)")))
			.andExpect(content().string(containsString("11/9 결근 확인")))
			.andExpect(content().string(containsString("<details id=\"check-panel\" open>")));

		Long first = repository.findByResolvedFalseOrderByIdAsc().get(0).getId();
		Long second = repository.findByResolvedFalseOrderByIdAsc().get(1).getId();
		mvc.perform(post("/check-items/" + first + "/resolve").param("back", "/lessons"))
			.andExpect(redirectedUrl("/lessons"));
		mvc.perform(post("/check-items/" + second + "/delete").param("back", "/lessons"))
			.andExpect(redirectedUrl("/lessons"));
		// 해결한 항목은 "해결된 항목"에서 다시 보고 되돌릴 수 있음, 삭제한 항목은 사라짐
		mvc.perform(get("/lessons"))
			.andExpect(content().string(containsString("확인 필요 (0)")))
			.andExpect(content().string(containsString("해결된 항목 (1)")))
			.andExpect(content().string(containsString("11/9 결근 확인")))
			.andExpect(content().string(containsString("11/2 14:00 해결")))
			.andExpect(content().string(not(containsString("지울 항목"))));
		mvc.perform(post("/check-items/" + first + "/reopen").param("back", "/lessons"))
			.andExpect(redirectedUrl("/lessons"));
		mvc.perform(get("/lessons"))
			.andExpect(content().string(containsString("확인 필요 (1)")))
			.andExpect(content().string(not(containsString("해결된 항목 ("))));

		mvc.perform(post("/check-items").param("content", " ").param("back", "/lessons"))
			.andExpect(flash().attribute("error", "내용을(를) 입력하세요."));
	}
}
