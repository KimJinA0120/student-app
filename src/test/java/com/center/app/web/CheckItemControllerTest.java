package com.center.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.center.app.checkitem.CheckItemRepository;
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
		mvc.perform(get("/lessons"))
			.andExpect(content().string(containsString("확인 필요 (0)")))
			.andExpect(content().string(not(containsString("11/9 결근 확인"))));

		mvc.perform(post("/check-items").param("content", " ").param("back", "/lessons"))
			.andExpect(flash().attribute("error", "내용을(를) 입력하세요."));
	}
}
