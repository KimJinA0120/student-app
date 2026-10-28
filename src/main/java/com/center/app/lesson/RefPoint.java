package com.center.app.lesson;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import com.center.app.common.PeriodUtil;

// 기준 시점: 날짜 + 교시 + 그 교시 안의 기준 시각 (진행 중 수업 판정에 사용)
// now = 사용자가 바꾸지 않은 "지금" 기준인지
public record RefPoint(LocalDate date, int period, LocalTime time, boolean now) {

	// 우선순위: 시각 지정 > 교시 지정 > 지금
	// - 시각을 지정하면 그 시각이 속한 교시
	// - 교시만 지정하면, 오늘의 지금 교시면 지금 시각 / 아니면 그 교시 시작 시각
	public static RefPoint resolve(LocalDateTime current, LocalDate date, Integer period, LocalTime time) {
		LocalDate today = current.toLocalDate();
		LocalTime nowTime = current.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
		LocalDate d = date == null ? today : date;
		if (time != null) {
			return new RefPoint(d, PeriodUtil.of(time), time, false);
		}
		int currentPeriod = PeriodUtil.of(nowTime);
		int p = period != null && PeriodUtil.isValid(period) ? period : currentPeriod;
		if (d.equals(today) && p == currentPeriod) {
			return new RefPoint(d, p, nowTime, date == null && period == null);
		}
		return new RefPoint(d, p, PeriodUtil.start(p), false);
	}

	// 교시가 끝난 뒤 = 지난 기록 (강사·교실 중복은 경고만, 강사는 전체에서 선택)
	public static boolean isPast(LocalDate date, int period, LocalDateTime current) {
		LocalDate today = current.toLocalDate();
		return date.isBefore(today)
			|| date.equals(today) && !current.toLocalTime().isBefore(PeriodUtil.end(period));
	}
}
