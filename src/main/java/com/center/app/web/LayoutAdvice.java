package com.center.app.web;

import com.center.app.checkitem.CheckItemService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 모든 화면의 공통 레이아웃(상단 [수업 기록] 버튼·사이드 패널)에 필요한 값을 넣음
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutAdvice {

	private final CheckItemService checkItemService;

	@ModelAttribute
	void layout(HttpServletRequest request, Model model) {
		// 버튼·수업 기록 폼이 저장 후 지금 보던 화면으로 돌아오도록
		model.addAttribute("currentUrl", currentUrl(request));
		model.addAttribute("checkItems", checkItemService.unresolved());
		model.addAttribute("resolvedCheckItems", checkItemService.resolved());
	}

	static String currentUrl(HttpServletRequest request) {
		String query = request.getQueryString();
		return request.getRequestURI() + (query == null ? "" : "?" + query);
	}
}
