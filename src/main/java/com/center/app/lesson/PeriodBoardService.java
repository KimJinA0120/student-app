package com.center.app.lesson;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.center.app.instructor.DaySchedule;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import com.center.app.instructor.InstructorScheduleService;
import com.center.app.lesson.PeriodBoard.InstructorLine;
import com.center.app.lesson.PeriodBoard.LessonInfo;
import com.center.app.lesson.PeriodBoard.RoomLine;
import com.center.app.room.RoomRepository;
import com.center.app.student.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 기준 시점의 근무 강사(InstructorScheduleService 재사용) + 진행 중 수업 + 교실 사용 현황 계산
// 수업 기록 폼(강사·교실 선택지, 빈 교실 자동 배정)과 희망 강사 알림에서 사용
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodBoardService {

	private final InstructorScheduleService scheduleService;
	private final LessonRepository lessonRepository;
	private final RoomRepository roomRepository;
	private final Clock clock;

	public PeriodBoard board(RefPoint ref) {
		return board(ref, null);
	}

	// excludeLessonId: 수정 중인 수업 자신은 "사용 중"으로 세지 않음
	public PeriodBoard board(RefPoint ref, Long excludeLessonId) {
		Map<Long, LessonInfo> lessonByInstructor = new HashMap<>();
		Map<Long, LessonInfo> lessonByRoom = new HashMap<>();
		Map<Long, String> unscheduledNames = new HashMap<>();
		for (Lesson l : lessonRepository.findWithDetailsByDateAndPeriod(ref.date(), ref.period())) {
			if (Objects.equals(l.getId(), excludeLessonId) || !l.isOngoingAt(ref.date(), ref.period(), ref.time())) {
				continue;
			}
			LessonInfo info = new LessonInfo(l.getId(), l.getRoom().getName(), l.getInstructor().getName(),
				l.getStudents().stream().map(Student::getName).sorted().toList(), l.getStartTime(), l.getEndTime());
			lessonByInstructor.put(l.getInstructor().getId(), info);
			lessonByRoom.put(l.getRoom().getId(), info);
			unscheduledNames.put(l.getInstructor().getId(), l.getInstructor().getName());
		}
		// 조퇴는 누른 교시까지 근무로 남고(이후 교시는 근무 계산에서 이미 빠짐) 그 교시엔 "조퇴" 표시
		DaySchedule day = scheduleService.getSchedule(ref.date());
		Set<Long> leftEarly = day.leaveEarly().keySet();
		PeriodSchedule schedule = day.period(ref.period());
		List<InstructorLine> lines = new ArrayList<>();
		for (Entry e : schedule.confirmed()) {
			lines.add(line(e, false, leftEarly, lessonByInstructor));
		}
		for (Entry e : schedule.conditional()) {
			lines.add(line(e, true, leftEarly, lessonByInstructor));
		}
		// 배치표에는 없지만 수업 기록이 있는 강사도 보여야 [종료]·[취소]를 할 수 있음
		lines.stream().map(InstructorLine::instructorId).toList().forEach(unscheduledNames::remove);
		unscheduledNames.entrySet().stream()
			.sorted(Map.Entry.comparingByValue())
			.forEach(u -> lines.add(new InstructorLine(u.getKey(), u.getValue(), null, false, false,
				leftEarly.contains(u.getKey()), lessonByInstructor.get(u.getKey()))));

		List<RoomLine> rooms = roomRepository.findAllByOrderBySortOrderAsc().stream()
			.map(r -> new RoomLine(r.getId(), r.getName(), lessonByRoom.get(r.getId())))
			.toList();
		return new PeriodBoard(ref, lines, rooms, RefPoint.isPast(ref.date(), ref.period(), LocalDateTime.now(clock)));
	}

	private InstructorLine line(Entry e, boolean conditional, Set<Long> leftEarly, Map<Long, LessonInfo> lessons) {
		return new InstructorLine(e.instructorId(), e.name(), e.role(), conditional, true,
			leftEarly.contains(e.instructorId()), lessons.get(e.instructorId()));
	}
}
