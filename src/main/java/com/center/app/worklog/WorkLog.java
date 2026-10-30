package com.center.app.worklog;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import com.center.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 업무일지 하루 한 줄 (엑셀 "업무일지" 시트와 같은 칸)
// 숫자는 수업 기록에서 미리 채워 주지만 저장하는 값은 매니저가 확인한 값 (그날 보고한 숫자를 남기는 기록이라)
// 요일·계·누적 합계는 저장하지 않고 계산
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkLog extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 하루에 한 줄
	@Column(name = "log_date", nullable = false, unique = true)
	private LocalDate logDate;

	// 교시별 수업 강좌 수. 빈 칸 허용 (엑셀에서도 수업 없는 교시는 비워 둠)
	private Integer period1;
	private Integer period2;
	private Integer period3;
	private Integer period4;

	@Column(name = "new_count")
	private Integer newCount;

	@Column(name = "existing_count")
	private Integer existingCount;

	// "강사" 칸: 강사 출석 메모
	@Column(name = "instructor_note", length = 1000)
	private String instructorNote;

	@Column(name = "special_note", length = 2000)
	private String specialNote;

	public WorkLog(Info info) {
		change(info);
	}

	public void change(Info info) {
		this.logDate = info.logDate();
		this.period1 = info.period1();
		this.period2 = info.period2();
		this.period3 = info.period3();
		this.period4 = info.period4();
		this.newCount = info.newCount();
		this.existingCount = info.existingCount();
		this.instructorNote = info.instructorNote();
		this.specialNote = info.specialNote();
	}

	public Info info() {
		return new Info(logDate, period1, period2, period3, period4, newCount, existingCount, instructorNote,
			specialNote);
	}

	public String dayOfWeek() {
		return logDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
	}

	// 강좌 계. 1~4교시가 모두 빈 칸이면 null (엑셀의 IF(COUNT(...)=0,"",SUM(...))와 같음)
	public Integer lessonTotal() {
		return sum(period1, period2, period3, period4);
	}

	// 수강생 계
	public Integer studentTotal() {
		return sum(newCount, existingCount);
	}

	// [카톡 복사]로 단톡방에 붙여 넣을 글. 비어 있는 항목은 뺌
	public String kakaoText() {
		List<String> lines = new ArrayList<>();
		lines.add("[안양 상설교육장 업무일지]");
		lines.add(logDate.getMonthValue() + "/" + logDate.getDayOfMonth() + "(" + dayOfWeek() + ")");
		if (lessonTotal() != null) {
			lines.add("");
			lines.add("■ 수업 강좌 " + lessonTotal() + "개");
			lines.add("  1교시 " + zero(period1) + " · 2교시 " + zero(period2)
				+ " · 3교시 " + zero(period3) + " · 4교시 " + zero(period4));
		}
		if (studentTotal() != null) {
			if (lessonTotal() == null) {
				lines.add("");
			}
			lines.add("■ 수강생 " + studentTotal() + "명 (신규 " + zero(newCount) + " · 기존 " + zero(existingCount) + ")");
		}
		note(lines, "■ 강사", instructorNote);
		note(lines, "■ 특이사항", specialNote);
		return String.join("\n", lines);
	}

	private static void note(List<String> lines, String title, String text) {
		if (text == null) {
			return;
		}
		lines.add("");
		lines.add(title);
		lines.add(text);
	}

	private static Integer sum(Integer... values) {
		List<Integer> filled = Stream.of(values).filter(Objects::nonNull).toList();
		return filled.isEmpty() ? null : filled.stream().mapToInt(Integer::intValue).sum();
	}

	private static int zero(Integer value) {
		return value == null ? 0 : value;
	}

	// 등록·수정 폼에서 그대로 받는 값
	public record Info(LocalDate logDate, Integer period1, Integer period2, Integer period3, Integer period4,
			Integer newCount, Integer existingCount, String instructorNote, String specialNote) {
	}
}
