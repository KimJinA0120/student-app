package com.center.app.web;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.center.app.instructor.InstructorService;
import com.center.app.lesson.Reservation;
import com.center.app.lesson.ReservationService;
import com.center.app.student.Student;
import com.center.app.student.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 강사 시간표 칸의 메모 예약(글)·예약(등록 수강생)과 미배정 [배정]. 저장 후 보던 화면(back)으로 돌아감
// instructorId가 없으면 미배정 칸
// 쓰기 요청(저장·삭제·배정)은 synchronized로 한 번에 하나씩 처리:
//   미배정 칸은 instructor_id가 NULL이라 UNIQUE 제약이 막아 주지 않음 → 동시에 처음 저장하면 행이 2개 생길 수 있음
//   서버가 한 대(JVM 하나)라 자바 락으로 충분. 서비스(@Transactional) 바깥에서 잡아야 커밋이 끝난 뒤 락이 풀림
@Controller
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;
	private final InstructorService instructorService;
	private final StudentService studentService;

	// original = 화면에 보였던 글 (그 사이 다른 사람이 고쳤는지 비교용)
	@PostMapping("/reservations")
	public synchronized String save(@RequestParam(required = false) Long instructorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(defaultValue = "") String content,
			@RequestParam(required = false) String original,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			reservationService.save(instructorId, date, period, content, original);
			ra.addFlashAttribute("message", content.isBlank() ? "메모를 지웠습니다." : "메모를 저장했습니다.");
			return to;
		});
	}

	// 예약 수강생 고르기. 고른 수강생은 [수업시작] 폼에 미리 체크됨
	// 시간표에서는 큰 창(dialog)으로 띄우려고 modal=true로 창 안쪽 조각만 받아 감. 스크립트가 꺼져 있으면 한 화면으로 열림
	@GetMapping("/reservations/students")
	public String studentsForm(@RequestParam(required = false) Long instructorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) String back,
			@RequestParam(defaultValue = "false") boolean modal, Model model) {
		Reservation reservation = reservationService.find(instructorId, date, period).orElse(null);
		Set<Long> selected = reservation == null ? Set.of()
			: reservation.getStudents().stream().map(Student::getId).collect(Collectors.toSet());
		model.addAttribute("activeTab", "instructors");
		model.addAttribute("instructorId", instructorId);
		model.addAttribute("instructorName", instructorId == null ? null : instructorService.get(instructorId).getName());
		model.addAttribute("date", date);
		model.addAttribute("period", period);
		model.addAttribute("content", reservation == null ? "" : reservation.getContent());
		// 고른 수강생을 위로
		model.addAttribute("students", studentService.list("").stream()
			.sorted((a, b) -> Boolean.compare(selected.contains(b.id()), selected.contains(a.id())))
			.toList());
		model.addAttribute("selectedIds", selected);
		model.addAttribute("back", Urls.safeBack(back, "/instructors"));
		return modal ? "reservations/students :: panel" : "reservations/students";
	}

	@PostMapping("/reservations/students")
	public synchronized String saveStudents(@RequestParam(required = false) Long instructorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) Set<Long> studentIds,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			reservationService.saveStudents(instructorId, date, period, studentIds);
			ra.addFlashAttribute("message", "예약 수강생을 저장했습니다.");
			return to;
		});
	}

	// 근무가 빠진 칸·지난 칸의 [삭제]: 메모와 예약 수강생을 모두 지움
	@PostMapping("/reservations/delete")
	public synchronized String delete(@RequestParam(required = false) Long instructorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			reservationService.delete(instructorId, date, period);
			ra.addFlashAttribute("message", "예약을 지웠습니다.");
			return to;
		});
	}

	@PostMapping("/reservations/assign")
	public synchronized String assign(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Integer period, @RequestParam(required = false) Long instructorId,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/instructors");
		return Flash.handle(ra, to, () -> {
			reservationService.assign(date, period, instructorId);
			ra.addFlashAttribute("message", "예약을 배정했습니다.");
			return to;
		});
	}
}
