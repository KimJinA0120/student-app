package com.center.app.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 탭 화면 뼈대. 각 탭 기능을 만들 때 탭별 컨트롤러로 옮긴다
@Controller
public class PageController {

	@GetMapping("/")
	public String home() {
		return "redirect:/instructors";
	}

	@GetMapping("/managers")
	public String managers(Model model) {
		return placeholder(model, "managers", "운영 매니저");
	}

	@GetMapping("/lessons")
	public String lessons(Model model) {
		return placeholder(model, "lessons", "수업 기록");
	}

	private String placeholder(Model model, String tab, String title) {
		model.addAttribute("activeTab", tab);
		model.addAttribute("title", title);
		return "placeholder";
	}
}
