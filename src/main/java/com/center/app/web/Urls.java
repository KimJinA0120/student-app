package com.center.app.web;

// 화면 사이 이동 주소 처리
final class Urls {

	private Urls() {
	}

	// 폼·버튼이 넘겨준 "돌아갈 주소"는 이 사이트 안의 경로만 허용 (//다른사이트 같은 외부 주소로 보내지 않도록)
	static String safeBack(String back, String fallback) {
		if (back == null || !back.startsWith("/") || back.startsWith("//") || back.startsWith("/\\")) {
			return fallback;
		}
		return back;
	}
}
