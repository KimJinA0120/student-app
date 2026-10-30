package com.center.app.web;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.DaySchedule;
import com.center.app.instructor.InstructorScheduleService;
import com.center.app.lesson.LessonService;
import com.center.app.lesson.Reservation;
import com.center.app.lesson.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 강사 탭: 날짜별 보기(시간표) / 이름 검색 / 전체 달력
// 시간표 칸의 버튼은 각각 LessonController(수업)·ReservationController(예약)·LeaveEarlyController(조퇴)로 보냄
@Controller
@RequestMapping("/instructors")
@RequiredArgsConstructor
public class InstructorController {

	private final InstructorScheduleService scheduleService;
	private final LessonService lessonService;
	private final ReservationService reservationService;
	private final Clock clock;

	// 이 컨트롤러의 모든 화면에 공통으로 넣는 값
	@ModelAttribute
	void common(Model model) {
		model.addAttribute("activeTab", "instructors");
		// 교시 머리글용 "09:30~12:00" (인덱스 0 = 1교시)
		model.addAttribute("periodTimes", IntStream.rangeClosed(PeriodUtil.FIRST, PeriodUtil.LAST)
			.mapToObj(p -> PeriodUtil.start(p) + "~" + PeriodUtil.end(p)).toList());
	}

	// 날짜별 보기 (기본): 강사 × 교시 시간표, 칸마다 예약과 그 강사의 수업 기록. 맨 위는 미배정 예약 줄. 날짜가 없으면 오늘
	@GetMapping
	public String day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Model model) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate target = date == null ? now.toLocalDate() : date;
		LocalDate monday = target.with(DayOfWeek.MONDAY);
		DaySchedule day = scheduleService.getSchedule(target);

		model.addAttribute("date", target);
		model.addAttribute("week", monday.datesUntil(monday.plusDays(7)).toList());
		model.addAttribute("day", day);
		List<Reservation> reservations = reservationService.findByDate(target);
		model.addAttribute("rows", InstructorDayRow.of(day, lessonService.findByDate(target), reservations, now));
		model.addAttribute("unassigned", InstructorDayRow.unassigned(day, reservations, now));
		return "instructors/day";
	}

	// 이름 검색: 그 강사의 일정만 날짜순으로
	@GetMapping("/search")
	public String search(@RequestParam(defaultValue = "") String q, Model model) {
		String keyword = q.trim();
		if (keyword.isEmpty()) {
			return "redirect:/instructors";
		}
		model.addAttribute("q", keyword);
		model.addAttribute("results", scheduleService.searchByName(keyword));
		return "instructors/search";
	}

	// 전체 달력: 기간 전체를 월~일 주 단위로
	@GetMapping("/calendar")
	public String calendar(Model model) {
		List<DaySchedule> days = scheduleService.getSchedules(
			InstructorScheduleService.SEASON_START, InstructorScheduleService.SEASON_END);
		model.addAttribute("weeks", toWeeks(days));
		model.addAttribute("required", InstructorScheduleService.REQUIRED_PER_PERIOD);
		return "instructors/calendar";
	}

	// 7칸씩 끊은 주 목록. 기간 밖의 칸(첫 주 월요일 이전, 마지막 주 이후)은 null로 채움
	private List<List<DaySchedule>> toWeeks(List<DaySchedule> days) {
		List<DaySchedule> cells = new ArrayList<>();
		int blanksBefore = days.getFirst().date().getDayOfWeek().getValue() - 1; // 월=1
		for (int i = 0; i < blanksBefore; i++) {
			cells.add(null);
		}
		cells.addAll(days);
		while (cells.size() % 7 != 0) {
			cells.add(null);
		}
		List<List<DaySchedule>> weeks = new ArrayList<>();
		for (int i = 0; i < cells.size(); i += 7) {
			weeks.add(cells.subList(i, i + 7));
		}
		return weeks;
	}
}
