package com.center.app.web;

import java.time.LocalDate;

import com.center.app.instructor.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 강사 시간표 칸의 [조퇴]·조퇴 [취소] 버튼. 처리 후 보던 화면(back)으로 돌아감
// (칸의 [수업시작]·[추가]·[종료]·[취소]는 LessonController)
@Controller
@RequiredArgsConstructor
public class LeaveEarlyController {

	private final InstructorService instructorService;

	@PostMapping("/instructors/{id:\\d+}/leave-early")
	public String leaveEarly(@PathVariable Long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			instructorService.leaveEarly(id, date, period);
			ra.addFlashAttribute("message", "조퇴 처리했습니다. " + period + "교시 이후 근무에서 빠집니다.");
			return to;
		});
	}

	@PostMapping("/instructors/{id:\\d+}/leave-early/cancel")
	public String cancel(@PathVariable Long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			instructorService.cancelLeaveEarly(id, date);
			ra.addFlashAttribute("message", "조퇴를 취소했습니다.");
			return to;
		});
	}
}
