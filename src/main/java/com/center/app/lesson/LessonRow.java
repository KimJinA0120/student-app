package com.center.app.lesson;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 수업 기록 탭 목록 한 줄
public record LessonRow(
	Long id,
	LocalDate date,
	int period,
	String roomName,
	String instructorName,
	List<String> studentNames,
	LocalTime startTime,  // 비어 있으면 교시 시작
	LocalTime endTime     // 비어 있으면 교시 종료
) {

	// 시각을 따로 적은 수업만 "13:20~14:40"처럼 표시 (비어 있는 쪽은 교시 시각)
	public String timeText() {
		if (startTime == null && endTime == null) {
			return "";
		}
		return (startTime == null ? "" : startTime.toString()) + "~" + (endTime == null ? "" : endTime.toString());
	}
}
