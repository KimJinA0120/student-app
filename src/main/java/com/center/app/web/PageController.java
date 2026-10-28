package com.center.app.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 첫 화면: 강사 탭으로 이동 (탭별 화면은 각 탭 컨트롤러에 있음)
@Controller
public class PageController {

	@GetMapping("/")
	public String home() {
		return "redirect:/instructors";
	}
}
