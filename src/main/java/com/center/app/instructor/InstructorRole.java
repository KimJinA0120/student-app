package com.center.app.instructor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 주 / 보조 (표시용)
@Getter
@RequiredArgsConstructor
public enum InstructorRole {
	MAIN("주"),
	ASSIST("보조");

	private final String label;
}
