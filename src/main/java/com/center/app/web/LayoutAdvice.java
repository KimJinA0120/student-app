package com.center.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 모든 화면의 공통 레이아웃(상단 패널·사이드 패널)에 필요한 값을 넣음
@ControllerAdvice
public class LayoutAdvice {

	@ModelAttribute
	void layout(HttpServletRequest request, Model model) {
		// 패널 버튼·수업 기록 폼이 저장 후 지금 보던 화면으로 돌아오도록
		model.addAttribute("currentUrl", currentUrl(request));
	}

	static String currentUrl(HttpServletRequest request) {
		String query = request.getQueryString();
		return request.getRequestURI() + (query == null ? "" : "?" + query);
	}
}
