package com.center.app.common;

// 폼 입력 정리·검증 공통 함수. 오류는 IllegalArgumentException(한국어 메시지)으로 알림
public final class Inputs {

	private Inputs() {
	}

	public static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public static String required(String value, int max, String label) {
		String trimmed = blankToNull(value);
		if (trimmed == null) {
			throw new IllegalArgumentException(label + "을(를) 입력하세요.");
		}
		return limit(trimmed, max, label);
	}

	public static String optional(String value, int max, String label) {
		return limit(blankToNull(value), max, label);
	}

	public static String limit(String value, int max, String label) {
		if (value != null && value.length() > max) {
			throw new IllegalArgumentException(label + "은(는) " + max + "자까지 입력할 수 있습니다.");
		}
		return value;
	}
}
