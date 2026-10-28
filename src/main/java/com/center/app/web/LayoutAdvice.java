package com.center.app.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.center.app.lesson.PeriodBoardService;
import com.center.app.lesson.RefPoint;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 모든 화면의 공통 레이아웃(상단 근무 중인 강사 패널·사이드 패널)에 필요한 값을 넣음
// 기준 시점은 주소의 refDate·refPeriod·refTime으로 바꿈 (없으면 지금). 탭을 옮기면 다시 지금 기준
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutAdvice {

	static final List<String> REF_PARAMS = List.of("refDate", "refPeriod", "refTime");

	private final PeriodBoardService boardService;
	private final Clock clock;

	public record Param(String name, String value) {
	}

	@ModelAttribute
	void layout(HttpServletRequest request, Model model) {
		// 패널 버튼·수업 기록 폼이 저장 후 지금 보던 화면으로 돌아오도록
		model.addAttribute("currentUrl", currentUrl(request));

		// 형식이 틀린 값은 무시 (화면 전체가 오류 나지 않게)
		RefPoint ref = RefPoint.resolve(LocalDateTime.now(clock),
			parse(request.getParameter("refDate"), LocalDate::parse),
			parse(request.getParameter("refPeriod"), Integer::valueOf),
			parse(request.getParameter("refTime"), LocalTime::parse));
		model.addAttribute("panel", boardService.board(ref));
		List<Param> pageParams = pageParams(request);
		model.addAttribute("pageParams", pageParams);
		// 기준 시점 폼은 GET으로 지금 화면을 다시 엶 (POST 화면이면 강사 탭으로)
		String path = "GET".equals(request.getMethod()) ? request.getRequestURI() : "/instructors";
		model.addAttribute("refAction", path);
		model.addAttribute("nowUrl", path + query(pageParams));
	}

	private static String query(List<Param> params) {
		if (params.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Param p : params) {
			sb.append(sb.isEmpty() ? "?" : "&").append(URLEncoder.encode(p.name(), StandardCharsets.UTF_8))
				.append('=').append(URLEncoder.encode(p.value(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	static String currentUrl(HttpServletRequest request) {
		String query = request.getQueryString();
		return request.getRequestURI() + (query == null ? "" : "?" + query);
	}

	// 기준 시점을 바꿀 때 보던 화면의 다른 조건(날짜·검색어 등)은 유지
	private List<Param> pageParams(HttpServletRequest request) {
		List<Param> params = new ArrayList<>();
		if (!"GET".equals(request.getMethod())) {
			return params;
		}
		for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
			if (!REF_PARAMS.contains(e.getKey())) {
				for (String v : e.getValue()) {
					params.add(new Param(e.getKey(), v));
				}
			}
		}
		return params;
	}

	private static <T> T parse(String value, Function<String, T> parser) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return parser.apply(value.trim());
		} catch (RuntimeException e) {
			return null;
		}
	}
}
