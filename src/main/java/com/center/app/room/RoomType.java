package com.center.app.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomType {
	NORMAL("일반"),
	COUNSEL("상담실"),
	STORAGE("창고");

	private final String label;
}
