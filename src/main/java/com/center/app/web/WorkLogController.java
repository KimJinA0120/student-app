package com.center.app.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.center.app.worklog.WorkLog;
import com.center.app.worklog.WorkLogService;
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

// 업무일지 탭: 목록(누적 합계, 줄마다 [카톡 복사]) / 작성·수정·삭제
// 새로 쓸 때는 그날 수업 기록으로 숫자를 미리 채움. 날짜를 바꾸면 그 날짜로 다시 채움(/work-logs/new?date=)
@Controller
@RequestMapping("/work-logs")
@RequiredArgsConstructor
public class WorkLogController {

	private final WorkLogService service;
	private final Clock clock;

	// 작성·수정 폼. 빈 칸은 null로 들어옴
	public record WorkLogForm(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate,
		Integer period1,
		Integer period2,
		Integer period3,
		Integer period4,
		Integer newCount,
		Integer existingCount,
		String instructorNote,
		String specialNote
	) {

		static WorkLogForm of(WorkLog.Info i) {
			return new WorkLogForm(i.logDate(), i.period1(), i.period2(), i.period3(), i.period4(), i.newCount(),
				i.existingCount(), i.instructorNote(), i.specialNote());
		}

		WorkLog.Info info() {
			return new WorkLog.Info(logDate, period1, period2, period3, period4, newCount, existingCount,
				instructorNote, specialNote);
		}
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "workLogs";
	}

	@GetMapping
	public String list(Model model) {
		List<WorkLog> logs = service.list();
		model.addAttribute("logs", logs);
		model.addAttribute("totals", WorkLogService.totals(logs));
		return "work-logs/list";
	}

	// 날짜를 안 고르면 오늘. 그 날짜 일지가 이미 있으면 수정 화면으로 보냄 (하루 한 줄)
	@GetMapping("/new")
	public String newForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Model model, RedirectAttributes ra) {
		LocalDate day = date != null ? date : LocalDate.now(clock);
		Optional<WorkLog> existing = service.findByDate(day);
		if (existing.isPresent()) {
			ra.addFlashAttribute("warning", "이미 작성한 업무일지입니다. 여기서 수정하세요.");
			return "redirect:/work-logs/" + existing.get().getId() + "/edit";
		}
		return form(model, null, WorkLogForm.of(service.prefill(day)));
	}

	@GetMapping("/{id:\\d+}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		WorkLog log = service.get(id);
		model.addAttribute("kakaoText", log.kakaoText());
		return form(model, id, WorkLogForm.of(log.info()));
	}

	// 오류가 나면 redirect 대신 입력값을 유지한 채 폼을 다시 보여줌
	@PostMapping
	public String create(@ModelAttribute WorkLogForm form, BindingResult binding, Model model,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return form(model, null, form, "입력 형식이 올바르지 않습니다. 날짜와 숫자 칸을 확인하세요.");
		}
		try {
			service.create(form.info());
			ra.addFlashAttribute("message", "업무일지를 저장했습니다.");
			return "redirect:/work-logs";
		} catch (IllegalArgumentException e) {
			return form(model, null, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @ModelAttribute WorkLogForm form, BindingResult binding,
			Model model, RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return form(model, id, form, "입력 형식이 올바르지 않습니다. 날짜와 숫자 칸을 확인하세요.");
		}
		try {
			service.update(id, form.info());
			ra.addFlashAttribute("message", "저장했습니다.");
			return "redirect:/work-logs";
		} catch (IllegalArgumentException e) {
			return form(model, id, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes ra) {
		return Flash.handle(ra, "/work-logs/" + id + "/edit", () -> {
			service.delete(id);
			ra.addFlashAttribute("message", "삭제했습니다.");
			return "/work-logs";
		});
	}

	private String form(Model model, Long id, WorkLogForm form, String error) {
		model.addAttribute("error", error);
		return form(model, id, form);
	}

	private String form(Model model, Long id, WorkLogForm form) {
		model.addAttribute("workLogId", id);
		model.addAttribute("form", form);
		return "work-logs/form";
	}
}
