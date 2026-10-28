package com.center.app.web;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.center.app.manager.ManagerExceptionType;
import com.center.app.manager.ManagerService;
import com.center.app.manager.ManagerService.ShiftInput;
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

// 운영 매니저 탭: 날짜별 보기(기본) / 매니저 관리(정보·기본 근무표·예외)
@Controller
@RequestMapping("/managers")
@RequiredArgsConstructor
public class ManagerController {

	private static final List<String> DAY_LABELS = List.of("월", "화", "수", "목", "금", "토", "일");

	private final ManagerService managerService;

	// 날짜별 예외 추가 폼. 빈 칸은 null
	public record ExceptionForm(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
		ManagerExceptionType type,
		@DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
		@DateTimeFormat(pattern = "HH:mm") LocalTime endTime,
		String memo
	) {
	}

	@ModelAttribute("activeTab")
	String activeTab() {
		return "managers";
	}

	// 날짜별 보기: 주 이동 + 요일 탭, 그날 근무자·근무 시간과 휴무·시간 변경자
	@GetMapping
	public String day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Model model) {
		LocalDate target = date == null ? LocalDate.now() : date;
		LocalDate monday = target.with(DayOfWeek.MONDAY);
		model.addAttribute("date", target);
		model.addAttribute("week", monday.datesUntil(monday.plusDays(7)).toList());
		model.addAttribute("day", managerService.day(target));
		return "managers/day";
	}

	@GetMapping("/manage")
	public String list(Model model) {
		model.addAttribute("managers", managerService.findAll());
		model.addAttribute("days", DayOfWeek.values());
		model.addAttribute("dayLabels", DAY_LABELS);
		return "managers/manage";
	}

	@PostMapping
	public String create(@RequestParam(required = false) String name, @RequestParam(required = false) String phone,
			RedirectAttributes ra) {
		return Flash.handle(ra, "/managers/manage", () -> {
			Long id = managerService.create(name, phone);
			ra.addFlashAttribute("message", "매니저를 등록했습니다. 기본 근무표를 입력하세요.");
			return "/managers/" + id;
		});
	}

	@GetMapping("/{id:\\d+}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("manager", managerService.get(id));
		model.addAttribute("days", DayOfWeek.values());
		model.addAttribute("dayLabels", DAY_LABELS);
		model.addAttribute("exceptions", managerService.exceptionsOf(id));
		model.addAttribute("exceptionTypes", ManagerExceptionType.values());
		return "managers/detail";
	}

	@PostMapping("/{id:\\d+}")
	public String update(@PathVariable Long id, @RequestParam(required = false) String name,
			@RequestParam(required = false) String phone, RedirectAttributes ra) {
		return Flash.handle(ra, "/managers/" + id, () -> {
			managerService.update(id, name, phone);
			ra.addFlashAttribute("message", "저장했습니다.");
			return "/managers/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes ra) {
		return Flash.handle(ra, "/managers/" + id, () -> {
			managerService.delete(id);
			ra.addFlashAttribute("message", "삭제했습니다.");
			return "/managers/manage";
		});
	}

	// 요일마다 start_MONDAY, end_MONDAY 이름으로 들어옴. 둘 다 비운 요일은 근무 없음
	@PostMapping("/{id:\\d+}/shifts")
	public String saveShifts(@PathVariable Long id, @RequestParam Map<String, String> params, RedirectAttributes ra) {
		return Flash.handle(ra, "/managers/" + id, () -> {
			List<ShiftInput> shifts = new ArrayList<>();
			for (DayOfWeek day : DayOfWeek.values()) {
				LocalTime start = time(params.get("start_" + day));
				LocalTime end = time(params.get("end_" + day));
				if (start != null || end != null) {
					shifts.add(new ShiftInput(day, start, end));
				}
			}
			managerService.replaceShifts(id, shifts);
			ra.addFlashAttribute("message", "기본 근무표를 저장했습니다.");
			return "/managers/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/exceptions")
	public String addException(@PathVariable Long id, @ModelAttribute ExceptionForm form, BindingResult binding,
			RedirectAttributes ra) {
		if (binding.hasErrors()) {
			ra.addFlashAttribute("error", "입력 형식이 올바르지 않습니다. 날짜·시각을 확인하세요.");
			return "redirect:/managers/" + id;
		}
		return Flash.handle(ra, "/managers/" + id, () -> {
			managerService.addException(id, form.date(), form.type(), form.startTime(), form.endTime(), form.memo());
			ra.addFlashAttribute("message", "예외를 추가했습니다.");
			return "/managers/" + id;
		});
	}

	@PostMapping("/{id:\\d+}/exceptions/{exceptionId:\\d+}/delete")
	public String deleteException(@PathVariable Long id, @PathVariable Long exceptionId, RedirectAttributes ra) {
		return Flash.handle(ra, "/managers/" + id, () -> {
			managerService.deleteException(id, exceptionId);
			ra.addFlashAttribute("message", "예외를 삭제했습니다.");
			return "/managers/" + id;
		});
	}

	private static LocalTime time(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("시각 형식이 올바르지 않습니다: " + value);
		}
	}
}
