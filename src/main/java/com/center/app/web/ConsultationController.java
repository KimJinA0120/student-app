package com.center.app.web;

import java.time.Clock;
import java.time.LocalDate;

import com.center.app.consultation.Consultation;
import com.center.app.consultation.ConsultationService;
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

// 상담 탭: 상담일지 목록(이름·연락처 검색, 미등록만 보기) / 등록·수정·삭제
// [수강생 등록]은 수강생 등록 폼(/students/new?consultationId=)으로 보냄
@Controller
@RequestMapping("/consultations")
@RequiredArgsConstructor
public class ConsultationController {

	private final ConsultationService service;
	private final Clock clock;

	// 등록·수정 폼. 빈 칸은 null로 들어옴
	public record ConsultationForm(
		String name,
		String phone,
		String hopeClass,
		String hopeDay,
		String hopeTime,
		String memo,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate consultDate
	) {

		static ConsultationForm of(Consultation c) {
			return new ConsultationForm(c.getName(), c.getPhone(), c.getHopeClass(), c.getHopeDay(), c.getHopeTime(),
				c.getMemo(), c.getConsultDate());
		}

		Consultation.Info info() {
			return new Consultation.Info(name, phone, hopeClass, hopeDay, hopeTime, memo, consultDate);
		}
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "consultations";
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "false") boolean pending,
			Model model) {
		model.addAttribute("q", q.trim());
		model.addAttribute("pending", pending);
		model.addAttribute("consultations", service.list(q, pending));
		return "consultations/list";
	}

	// 상담일은 오늘로 미리 채움
	@GetMapping("/new")
	public String newForm(Model model) {
		return form(model, null, null,
			new ConsultationForm(null, null, null, null, null, null, LocalDate.now(clock)));
	}

	@GetMapping("/{id:\\d+}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Consultation c = service.get(id);
		return form(model, id, c, ConsultationForm.of(c));
	}

	// 오류가 나면 redirect 대신 입력값을 유지한 채 폼을 다시 보여줌
	@PostMapping
	public String create(@ModelAttribute ConsultationForm form, BindingResult binding, Model model,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return form(model, null, null, form, "입력 형식이 올바르지 않습니다. 상담일을 확인하세요.");
		}
		try {
			service.create(form.info());
			ra.addFlashAttribute("message", "상담을 등록했습니다.");
			return "redirect:/consultations";
		} catch (IllegalArgumentException e) {
			return form(model, null, null, form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @ModelAttribute ConsultationForm form, BindingResult binding,
			Model model, RedirectAttributes ra) {
		if (binding.hasErrors()) {
			return form(model, id, service.get(id), form, "입력 형식이 올바르지 않습니다. 상담일을 확인하세요.");
		}
		try {
			service.update(id, form.info());
			ra.addFlashAttribute("message", "저장했습니다.");
			return "redirect:/consultations";
		} catch (IllegalArgumentException e) {
			return form(model, id, service.get(id), form, e.getMessage());
		}
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes ra) {
		return Flash.handle(ra, "/consultations/" + id + "/edit", () -> {
			service.delete(id);
			ra.addFlashAttribute("message", "삭제했습니다.");
			return "/consultations";
		});
	}

	private String form(Model model, Long id, Consultation saved, ConsultationForm form, String error) {
		model.addAttribute("error", error);
		return form(model, id, saved, form);
	}

	// saved: 수정 화면에서 "등록한 수강생" 안내용 (새 상담이면 null)
	private String form(Model model, Long id, Consultation saved, ConsultationForm form) {
		model.addAttribute("consultationId", id);
		model.addAttribute("registeredStudent", saved == null ? null : saved.getStudent());
		model.addAttribute("form", form);
		return "consultations/form";
	}
}
