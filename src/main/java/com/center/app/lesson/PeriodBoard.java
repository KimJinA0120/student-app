package com.center.app.lesson;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import com.center.app.instructor.InstructorRole;

// 한 기준 시점(날짜·교시·시각)의 근무 강사·진행 중 수업·교실 사용 현황 (저장하지 않고 조회할 때마다 계산)
public record PeriodBoard(
	RefPoint ref,
	List<InstructorLine> instructors,  // 확정 근무 → 조건부 → 배치 밖이지만 수업 중인 강사 순
	List<RoomLine> rooms,              // 배정 순서대로
	boolean past                       // 교시가 이미 끝났는지 (지난 기록)
) {

	public record LessonInfo(Long lessonId, String roomName, String instructorName, List<String> studentNames,
			LocalTime startTime, LocalTime endTime) {

		public int studentCount() {
			return studentNames.size();
		}
	}

	public record InstructorLine(
		Long instructorId,
		String name,
		InstructorRole role,  // 역할 없으면 null
		boolean conditional,  // 조건부 "조"
		boolean scheduled,    // 배치표에 있는지 (false = 배치 밖이지만 수업 중)
		boolean leftEarly,    // 이 교시에 조퇴 처리됨 (이 교시까지 근무지만 새 수업은 맡기지 않음)
		LessonInfo lesson     // 진행 중 수업, 없으면 null
	) {

		public boolean busy() {
			return lesson != null;
		}

		// 지금 수업을 맡길 수 있는 강사 (수업 중도 아니고 조퇴도 아님)
		public boolean free() {
			return lesson == null && !leftEarly;
		}
	}

	public record RoomLine(Long roomId, String name, LessonInfo lesson) {

		public boolean used() {
			return lesson != null;
		}
	}

	// 빈 교실 자동 배정: 배정 순서상 첫 번째 빈 교실, 없으면 null
	public Long firstFreeRoomId() {
		return rooms.stream().filter(r -> !r.used()).map(RoomLine::roomId).findFirst().orElse(null);
	}

	public InstructorLine instructor(Long instructorId) {
		return instructors.stream().filter(i -> Objects.equals(i.instructorId(), instructorId)).findFirst().orElse(null);
	}
}
