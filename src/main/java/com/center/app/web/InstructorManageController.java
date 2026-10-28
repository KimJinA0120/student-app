package com.center.app.web;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRole;
import com.center.app.instructor.InstructorService;
import com.center.app.instructor.InstructorService.SlotInput;
import com.center.app.instructor.InstructorSlot;
import com.center.app.instructor.OverrideType;
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

// 강사 관리: 강사 등록·수정·삭제, 기본 배치, 날짜별 예외
// 저장 후에는 항상 redirect (새로고침해도 같은 요청이 다시 전송되지 않도록: Post-Redirect-Get)
@Controller
@RequestMapping("/instructors")
@RequiredArgsConstructor
public class InstructorManageController {

	private final InstructorService instructorService;

	// 날짜별 예외 추가 폼. 빈 칸은 null로 들어옴 (종료일·교시·역할·메모는 선택)
	public record OverrideForm(
		OverrideType type,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		Set<Integer> periods,
		InstructorRole role,
		String memo
	) {
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "instructors";
	}

	@GetMapping("/manage")
	public String list(Model model) {
		model.addAttribute("instructors", instructorService.findAll());
		return "instructors/manage";
	}

	@PostMapping
	public String create(@RequestParam String name, @RequestParam(required = false) String phone,
			RedirectAttributes ra) {
		return handle(ra, "/instructors/manage", () -> {
			Long id = instructorService.create(name, phone);
			ra.addFlashAttribute("message", "강사를 등록했습니다. 기본 배치를 입력하세요.");
			return "/instructors/" + id;
		});
	}

	@GetMapping("/{id:\\d+}")
	public String detail(@PathVariable Long id, Model model) {
		Instructor instructor = instructorService.get(id);
		// "MONDAY_1" → 기본 배치 칸 (화면 표에서 요일·교시로 찾기 위함)
		Map<String, InstructorSlot> slots = instructor.getSlots().stream()
			.collect(Collectors.toMap(s -> s.getDayOfWeek() + "_" + s.getPeriod(), Function.identity()));
		model.addAttribute("instructor", instructor);
		model.addAttribute("slots", slots);
		model.addAttribute("days", DayOfWeek.values());
		model.addAttribute("dayLabels", List.of("월", "화", "수", "목", "금", "토", "일"));
		model.addAttribute("overrides", instructorService.overridesOf(id));
		model.addAttribute("overrideTypes", OverrideType.values());
		model.addAttribute("roles", InstructorRole.values());
		return "instructors/detail";
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @RequestParam String name,
			@RequestParam(required = false) String phone, RedirectAttributes ra) {
		return handle(ra, "/instructors/" + id, () -> {
			instructorService.update(id, name, phone);
			ra.addFlashAttribute("message", "저장했습니다.");
			return "/instructors/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes ra) {
		return handle(ra, "/instructors/" + id, () -> {
			instructorService.delete(id);
			ra.addFlashAttribute("message", "삭제했습니다.");
			return "/instructors/manage";
		});
	}

	// 표의 칸마다 role_MONDAY_1, note_MONDAY_1 이름으로 들어옴. 역할을 비운 칸은 배치 없음
	@PostMapping("/{id:\\d+}/slots")
	public String saveSlots(@PathVariable Long id, @RequestParam Map<String, String> params,
			RedirectAttributes ra) {
		List<SlotInput> slots = new ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			for (int p = PeriodUtil.FIRST; p <= PeriodUtil.LAST; p++) {
				String role = params.get("role_" + day + "_" + p);
				if (role != null && !role.isBlank()) {
					slots.add(new SlotInput(day, p, InstructorRole.valueOf(role), params.get("note_" + day + "_" + p)));
				}
			}
		}
		return handle(ra, "/instructors/" + id, () -> {
			instructorService.replaceSlots(id, slots);
			ra.addFlashAttribute("message", "기본 배치를 저장했습니다.");
			return "/instructors/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/overrides")
	public String addOverride(@PathVariable Long id, @ModelAttribute OverrideForm form, BindingResult binding,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			ra.addFlashAttribute("error", "입력 형식이 올바르지 않습니다. 날짜를 확인하세요.");
			return "redirect:/instructors/" + id;
		}
		return handle(ra, "/instructors/" + id, () -> {
			instructorService.addOverride(id, form.type(), form.startDate(), form.endDate(), form.periods(),
				form.role(), form.memo());
			ra.addFlashAttribute("message", "예외를 추가했습니다.");
			return "/instructors/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/overrides/{overrideId:\\d+}/delete")
	public String deleteOverride(@PathVariable Long id, @PathVariable Long overrideId, RedirectAttributes ra) {
		return handle(ra, "/instructors/" + id, () -> {
			instructorService.deleteOverride(id, overrideId);
			ra.addFlashAttribute("message", "예외를 삭제했습니다.");
			return "/instructors/" + id;
		});
	}

	// 서비스의 입력 오류 메시지를 화면 위쪽에 띄우고 원래 화면으로 돌려보냄
	// action은 성공 시 이동할 주소를 돌려줌
	private String handle(RedirectAttributes ra, String backTo, Supplier<String> action) {
		try {
			return "redirect:" + action.get();
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:" + backTo;
		}
	}
}
