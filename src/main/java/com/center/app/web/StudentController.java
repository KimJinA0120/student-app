package com.center.app.web;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorService;
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

// 수강생 탭: 목록(이름 검색·체크박스로 수업시작) / 상세 / 등록·수정·삭제
@Controller
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

	private final StudentService studentService;
	private final InstructorService instructorService;

	// 등록·수정 폼. 빈 칸은 null로 들어옴
	public record StudentForm(
		String name,
		String phone,
		Integer age,
		String hopeClass,
		String attendId,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		String memo,
		Set<Long> hopeInstructorIds
	) {

		static StudentForm of(Student s) {
			return new StudentForm(s.getName(), s.getPhone(), s.getAge(), s.getHopeClass(), s.getAttendId(),
				s.getStartDate(), s.getMemo(),
				s.getHopeInstructors().stream().map(Instructor::getId).collect(Collectors.toSet()));
		}

		Student.Info info() {
			return new Student.Info(name, phone, age, hopeClass, attendId, startDate, memo);
		}
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "students";
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("q", q.trim());
		model.addAttribute("students", studentService.list(q));
		return "students/list";
	}

	@GetMapping("/{id:\\d+}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("student", studentService.get(id));
		model.addAttribute("visitCount", studentService.visitCount(id));
		model.addAttribute("lessons", studentService.lessonsOf(id));
		return "students/detail";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		return form(model, null, new StudentForm(null, null, null, null, null, null, null, Set.of()));
	}

	@GetMapping("/{id:\\d+}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		return form(model, id, StudentForm.of(studentService.get(id)));
	}

	// 입력 칸이 많아서, 오류가 나면 redirect 대신 입력값을 유지한 채 폼을 다시 보여줌
	@PostMapping
	public String create(@ModelAttribute StudentForm form, BindingResult binding, Model model, RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return formWithError(model, null, form, "입력 형식이 올바르지 않습니다. 나이·날짜를 확인하세요.");
		}
		try {
			Long id = studentService.create(form.info(), form.hopeInstructorIds());
			ra.addFlashAttribute("message", "수강생을 등록했습니다.");
			return "redirect:/students/" + id;
		} catch (IllegalArgumentException e) {
			return formWithError(model, null, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @ModelAttribute StudentForm form, BindingResult binding, Model model,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return formWithError(model, id, form, "입력 형식이 올바르지 않습니다. 나이·날짜를 확인하세요.");
		}
		try {
			studentService.update(id, form.info(), form.hopeInstructorIds());
			ra.addFlashAttribute("message", "저장했습니다.");
			return "redirect:/students/" + id;
		} catch (IllegalArgumentException e) {
			return formWithError(model, id, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes ra) {
		return Flash.handle(ra, "/students/" + id, () -> {
			studentService.delete(id);
			ra.addFlashAttribute("message", "삭제했습니다.");
			return "/students";
		});
	}

	private String formWithError(Model model, Long id, StudentForm form, String error) {
		model.addAttribute("error", error);
		return form(model, id, form);
	}

	private String form(Model model, Long id, StudentForm form) {
		model.addAttribute("studentId", id);
		model.addAttribute("form", form);
		model.addAttribute("instructors", instructorService.findAll());
		return "students/form";
	}
}
