package com.center.app.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.center.app.instructor.InstructorService;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonService;
import com.center.app.lesson.LessonService.LessonInput;
import com.center.app.lesson.LessonService.SaveResult;
import com.center.app.lesson.PeriodBoard;
import com.center.app.lesson.PeriodBoard.InstructorLine;
import com.center.app.lesson.PeriodBoardService;
import com.center.app.lesson.RefPoint;
import com.center.app.student.Student;
import com.center.app.student.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 수업 기록: 목록(수업 기록 탭) / 기록 폼(새로·수정) / 삭제·종료·수강생 추가
// 폼은 4가지 방법으로 열림 (상단 버튼 / 패널 [수업시작] / 수강생 목록 체크 / 수강생 상세) → 모두 GET /lessons/new + 미리 채울 값
@Controller
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonController {

	private final LessonService lessonService;
	private final PeriodBoardService boardService;
	private final StudentService studentService;
	private final InstructorService instructorService;
	private final Clock clock;

	// 폼 값. GET으로 미리 채울 값을 받을 때와 POST로 저장할 때 같이 씀. back = 저장 후 돌아갈 화면
	public record LessonForm(
		Set<Long> studentIds,
		Long instructorId,
		Long roomId,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
		Integer period,
		@DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
		@DateTimeFormat(pattern = "HH:mm") LocalTime endTime,
		String back
	) {

		LessonInput input() {
			return new LessonInput(date, period, roomId, instructorId, startTime, endTime, studentIds);
		}
	}

	// 강사·강의실 선택지 한 줄. free = 지금 수업을 맡길 수 있는 강사 (희망 강사 알림용)
	public record Option(Long id, String name, String label, boolean disabled, boolean free) {
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "lessons";
	}

	// 수업 기록 탭: 최근 날짜순, 수강생·강사 이름 검색
	@GetMapping
	public String list(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("q", q.trim());
		model.addAttribute("lessons", lessonService.list(q));
		return "lessons/list";
	}

	// 형식이 틀린 값(날짜 등)은 비운 채로 폼을 염
	@GetMapping("/new")
	public String newForm(@ModelAttribute("lessonForm") LessonForm form, BindingResult binding, Model model) {
		return form(model, null, form);
	}

	// 날짜·교시를 바꾸면 화면이 이 주소로 다시 열림(선택지 갱신) → 그때는 주소의 값을 우선
	@GetMapping("/{id:\\d+}/edit")
	public String editForm(@PathVariable Long id, @ModelAttribute("lessonForm") LessonForm form,
			BindingResult binding, Model model) {
		if (form.date() == null) {
			Lesson l = lessonService.get(id);
			form = new LessonForm(l.getStudents().stream().map(Student::getId).collect(Collectors.toSet()),
				l.getInstructor().getId(), l.getRoom().getId(), l.getLessonDate(), l.getPeriod(),
				l.getStartTime(), l.getEndTime(), form.back());
		}
		return form(model, id, form);
	}

	// 입력 오류면 redirect 대신 입력값을 유지한 채 폼을 다시 보여줌
	@PostMapping
	public String create(@ModelAttribute("lessonForm") LessonForm form, BindingResult binding, Model model,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return formWithError(model, null, form, "입력 형식이 올바르지 않습니다. 날짜·시각을 확인하세요.");
		}
		try {
			SaveResult result = lessonService.create(form.input());
			return saved(ra, result, "수업을 기록했습니다.", form.back());
		} catch (IllegalArgumentException e) {
			return formWithError(model, null, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @ModelAttribute("lessonForm") LessonForm form, BindingResult binding,
			Model model, RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return formWithError(model, id, form, "입력 형식이 올바르지 않습니다. 날짜·시각을 확인하세요.");
		}
		try {
			SaveResult result = lessonService.update(id, form.input());
			return saved(ra, result, "수업 기록을 저장했습니다.", form.back());
		} catch (IllegalArgumentException e) {
			return formWithError(model, id, form, e.getMessage());
		}
	}

	// [취소](패널) / [삭제](수정 화면)
	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/lessons");
		return Flash.handle(ra, to, () -> {
			lessonService.delete(id);
			ra.addFlashAttribute("message", "수업 기록을 삭제했습니다.");
			return to;
		});
	}

	// [종료](패널): 종료 시각 = 지금
	@PostMapping("/{id:\\d+}/end")
	public String end(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/lessons");
		return Flash.handle(ra, to, () -> {
			lessonService.end(id);
			ra.addFlashAttribute("message", "수업을 종료했습니다.");
			return to;
		});
	}

	// [추가](패널): 진행 중 수업에 수강생 추가
	@GetMapping("/{id:\\d+}/students")
	public String addStudentsForm(@PathVariable Long id, @RequestParam(required = false) String back, Model model) {
		Lesson lesson = lessonService.get(id);
		Set<Long> already = lesson.getStudents().stream().map(Student::getId).collect(Collectors.toSet());
		model.addAttribute("lesson", lesson);
		model.addAttribute("students", studentService.list("").stream()
			.filter(s -> !already.contains(s.id())).toList());
		model.addAttribute("selectedIds", Set.of());
		model.addAttribute("back", Urls.safeBack(back, "/lessons"));
		return "lessons/add-students";
	}

	@PostMapping("/{id:\\d+}/students")
	public String addStudents(@PathVariable Long id, @RequestParam(required = false) Set<Long> studentIds,
			@RequestParam(required = false) String back, RedirectAttributes ra) {
		String to = Urls.safeBack(back, "/lessons");
		return Flash.handle(ra, "/lessons/" + id + "/students?back=" + URLEncoder.encode(to, StandardCharsets.UTF_8), () -> {
			lessonService.addStudents(id, studentIds);
			ra.addFlashAttribute("message", "수강생을 추가했습니다.");
			return to;
		});
	}

	private String saved(RedirectAttributes ra, SaveResult result, String message, String back) {
		ra.addFlashAttribute("message", message);
		if (result.warning() != null) {
			ra.addFlashAttribute("warning", result.warning());
		}
		return "redirect:" + Urls.safeBack(back, "/lessons");
	}

	private String formWithError(Model model, Long id, LessonForm form, String error) {
		model.addAttribute("error", error);
		return form(model, id, form);
	}

	// 날짜·교시 기준으로 강사·강의실 선택지와 빈 교실 자동 배정을 계산해 폼을 보여줌
	private String form(Model model, Long lessonId, LessonForm in) {
		RefPoint ref = RefPoint.resolve(LocalDateTime.now(clock), in.date(), in.period(), null);
		PeriodBoard board = boardService.board(ref, lessonId);
		Long roomId = in.roomId() != null ? in.roomId() : board.firstFreeRoomId();
		LessonForm form = new LessonForm(in.studentIds() == null ? Set.of() : in.studentIds(), in.instructorId(),
			roomId, ref.date(), ref.period(), in.startTime(), in.endTime(), in.back());

		model.addAttribute("lessonId", lessonId);
		model.addAttribute("lessonForm", form);
		model.addAttribute("past", board.past());
		model.addAttribute("instructorOptions", instructorOptions(board, form.instructorId()));
		model.addAttribute("roomOptions", board.rooms().stream()
			.map(r -> new Option(r.roomId(), r.name(), r.name() + (r.used() ? " (사용 중 · " + r.lesson().instructorName() + ")" : ""),
				r.used() && !board.past(), false))
			.toList());
		model.addAttribute("noFreeRoom", board.firstFreeRoomId() == null);
		// 선택된 수강생을 위로
		model.addAttribute("students", studentService.list("").stream()
			.sorted((a, b) -> Boolean.compare(form.studentIds().contains(b.id()), form.studentIds().contains(a.id())))
			.toList());
		model.addAttribute("selectedIds", form.studentIds());
		return "lessons/form";
	}

	// 지금·앞으로의 수업: 근무 중인 강사(수업 중이면 선택 불가) / 지난 기록: 전체 강사
	private List<Option> instructorOptions(PeriodBoard board, Long selectedId) {
		List<Option> options = new ArrayList<>();
		if (board.past()) {
			instructorService.findAll().forEach(i -> options.add(new Option(i.getId(), i.getName(), i.getName(), false, false)));
			return options;
		}
		for (InstructorLine i : board.instructors()) {
			String label = i.name()
				+ (i.conditional() ? " (조)" : "")
				+ (i.status() != null ? " · " + i.status().getLabel() : "")
				+ (i.busy() ? " · 수업 중 " + i.lesson().roomName() : "");
			options.add(new Option(i.instructorId(), i.name(), label, i.busy(), i.free()));
		}
		// 미리 채운 강사가 근무표에 없어도 값이 사라지지 않게
		if (selectedId != null && board.instructor(selectedId) == null) {
			instructorService.findAll().stream().filter(i -> i.getId().equals(selectedId)).findFirst()
				.ifPresent(i -> options.add(new Option(i.getId(), i.getName(), i.getName() + " (근무 아님)", false, false)));
		}
		return options;
	}
}
