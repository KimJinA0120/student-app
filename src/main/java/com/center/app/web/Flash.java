package com.center.app.web;

import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 저장 요청 처리 공통: 성공하면 action이 돌려준 주소로, 서비스의 입력 오류면 메시지를 flash로 담아 backTo로 redirect
// 두 사람이 거의 동시에 같은 칸에 저장해 UNIQUE 제약에 걸리면 에러 화면 대신 다시 해 달라고 안내
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
		} catch (DataIntegrityViolationException e) {
			ra.addFlashAttribute("error", "다른 분이 같은 내용을 먼저 저장했습니다. 화면을 확인하고 다시 해 주세요.");
			return "redirect:" + backTo;
		}
	}
}
