package com.center.app.web;

import java.util.function.Supplier;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 저장 요청 처리 공통: 성공하면 action이 돌려준 주소로, 서비스의 입력 오류면 메시지를 flash로 담아 backTo로 redirect
// (저장 후에는 항상 redirect: 새로고침해도 같은 요청이 다시 전송되지 않도록, Post-Redirect-Get)
final class Flash {

	private Flash() {
	}

	static String handle(RedirectAttributes ra, String backTo, Supplier<String> action) {
		try {
			return "redirect:" + action.get();
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:" + backTo;
		}
	}
}
