package com.center.app.web;

import java.time.LocalDate;

import com.center.app.instructor.DayStatus;
import com.center.app.instructor.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 근무 중인 강사 패널의 휴식·외출·조퇴·[복귀] 버튼. 처리 후 보던 화면(back)으로 돌아감
// ([추가]·[종료]·[취소]·[수업시작]은 LessonController)
@Controller
@RequiredArgsConstructor
public class PanelController {

	private final InstructorService instructorService;

	@PostMapping("/instructors/{id:\\d+}/day-status")
	public String change(@PathVariable Long id, @RequestParam DayStatus status,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			instructorService.changeDayStatus(id, date, status, period);
			ra.addFlashAttribute("message", status.getLabel() + "(으)로 바꿨습니다.");
			return to;
		});
	}

	@PostMapping("/instructors/{id:\\d+}/day-status/return")
	public String returnToWork(@PathVariable Long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			instructorService.returnToWork(id, date);
			ra.addFlashAttribute("message", "복귀했습니다.");
			return to;
		});
	}
}
