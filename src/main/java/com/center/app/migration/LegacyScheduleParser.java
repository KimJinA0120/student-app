package com.center.app.migration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.center.app.instructor.InstructorRole;
import com.center.app.migration.LegacySchedule.Conditional;
import com.center.app.migration.LegacySchedule.LegacyDay;
import com.center.app.migration.LegacySchedule.WeeklyRow;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// 기존 강사 일정 HTML 파서 (한 번만 쓰는 이전용)
// - 하단 <script>의 DB 객체: 연락처(ph), 날짜별 확정(c)·조건부(cd)·메모(n)
// - 요일별 보기 표: 요일·교시별 기본 배치와 주/보조
// - 확인 필요 목록
public class LegacyScheduleParser {

	// HTML 날짜는 "10/27"처럼 연도가 없음. 요일(w)과 맞는지 검사해서 잘못된 연도를 걸러냄
	static final int YEAR = 2026;
	static final int PERIODS = 4;

	private static final Pattern DAY_SECTION = Pattern.compile("<section class=\"day\" id=\"s(\\d)\">");
	private static final Pattern BLOCK = Pattern.compile("<section class=\"blk\">(.*?)</section>", Pattern.DOTALL);
	private static final Pattern ROW = Pattern.compile(
		"<div class=\"r( cd)?\"><div><div class=\"n\">(.*?)<small>(.*?)</small></div>(?:<div class=\"t\">(.*?)</div>)?");
	private static final Pattern CHECK_LIST = Pattern.compile("<h2>확인 필요</h2><ul>(.*?)</ul>", Pattern.DOTALL);
	private static final Pattern LIST_ITEM = Pattern.compile("<li>(.*?)</li>", Pattern.DOTALL);

	// DB 객체를 그대로 받는 형태 (cd는 [이름, 메모] 배열)
	record RawDb(Map<String, String> ph, List<RawDate> dates) {
	}

	record RawDate(String d, String w, List<List<String>> c, List<List<List<String>>> cd, List<String> n) {
	}

	public LegacySchedule parse(String html) {
		RawDb db = parseDb(html);
		return new LegacySchedule(
			new LinkedHashMap<>(db.ph()),
			parseWeekly(html),
			db.dates().stream().map(this::toDay).toList(),
			parseCheckItems(html));
	}

	private RawDb parseDb(String html) {
		String marker = "var DB=";
		int start = html.indexOf(marker);
		if (start < 0) {
			throw new IllegalArgumentException("HTML에서 DB 데이터를 찾을 수 없습니다.");
		}
		// DB 객체 뒤에 다른 스크립트가 이어지므로 첫 JSON 값만 읽고 나머지는 무시
		return JsonMapper.builder().build()
			.readerFor(RawDb.class)
			.without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.readValue(html.substring(start + marker.length()));
	}

	private LegacyDay toDay(RawDate raw) {
		String[] md = raw.d().split("/");
		LocalDate date = LocalDate.of(YEAR, Integer.parseInt(md[0]), Integer.parseInt(md[1]));
		String weekday = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
		if (!weekday.equals(raw.w())) {
			throw new IllegalArgumentException(raw.d() + "의 요일이 맞지 않습니다: " + raw.w() + " ≠ " + weekday);
		}
		List<List<Conditional>> conditional = raw.cd().stream()
			.map(list -> list.stream().map(pair -> new Conditional(pair.get(0), pair.get(1))).toList())
			.toList();
		return new LegacyDay(date, raw.c(), conditional, raw.n());
	}

	private Map<DayOfWeek, List<List<WeeklyRow>>> parseWeekly(String html) {
		Map<DayOfWeek, List<List<WeeklyRow>>> weekly = new EnumMap<>(DayOfWeek.class);
		Matcher day = DAY_SECTION.matcher(html);
		while (day.find()) {
			DayOfWeek dayOfWeek = DayOfWeek.of(Integer.parseInt(day.group(1)) + 1); // s0 = 월요일
			Matcher block = BLOCK.matcher(html);
			block.region(day.end(), html.length());
			List<List<WeeklyRow>> periods = new ArrayList<>();
			while (periods.size() < PERIODS && block.find()) {
				periods.add(parseRows(block.group(1)));
			}
			weekly.put(dayOfWeek, periods);
		}
		if (weekly.size() != 7) {
			throw new IllegalArgumentException("요일별 보기는 7일이어야 합니다: " + weekly.size());
		}
		return weekly;
	}

	private List<WeeklyRow> parseRows(String block) {
		List<WeeklyRow> rows = new ArrayList<>();
		Matcher row = ROW.matcher(block);
		while (row.find()) {
			if (row.group(1) != null) {
				continue; // 조건부 행
			}
			InstructorRole role = switch (row.group(3)) {
				case "주" -> InstructorRole.MAIN;
				case "보조" -> InstructorRole.ASSIST;
				default -> throw new IllegalArgumentException("알 수 없는 역할: " + row.group(3));
			};
			String note = row.group(4) == null || row.group(4).isBlank() ? null : row.group(4);
			rows.add(new WeeklyRow(row.group(2), role, note));
		}
		return rows;
	}

	private List<String> parseCheckItems(String html) {
		Matcher list = CHECK_LIST.matcher(html);
		if (!list.find()) {
			return List.of();
		}
		List<String> items = new ArrayList<>();
		Matcher item = LIST_ITEM.matcher(list.group(1));
		while (item.find()) {
			items.add(item.group(1).trim());
		}
		return items;
	}
}
