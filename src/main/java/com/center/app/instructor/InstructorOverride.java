package com.center.app.instructor;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

import com.center.app.common.BaseEntity;
import com.center.app.common.PeriodSetConverter;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 날짜별 예외 (결근 / 조건부 / 추가 배치 / 메모). 하루짜리는 startDate = endDate
@Entity
@Table(name = "instructor_override",
	indexes = @Index(name = "idx_override_range", columnList = "start_date, end_date"),
	check = @CheckConstraint(name = "ck_override_range", constraint = "end_date >= start_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorOverride extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Instructor instructor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private OverrideType type;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	// 빈 Set = 전 교시
	@Convert(converter = PeriodSetConverter.class)
	@Column(length = 10)
	private Set<Integer> periods = new TreeSet<>();

	// EXTRA일 때 주/보조 표시용 (선택)
	@Enumerated(EnumType.STRING)
	@Column(columnDefinition = "varchar(10)")
	private InstructorRole role;

	@Column(length = 500)
	private String memo;

	public InstructorOverride(Instructor instructor, OverrideType type, LocalDate startDate, LocalDate endDate,
			Set<Integer> periods, InstructorRole role, String memo) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
		}
		this.instructor = instructor;
		this.type = type;
		this.startDate = startDate;
		this.endDate = endDate;
		this.periods = periods == null ? new TreeSet<>() : new TreeSet<>(periods);
		this.role = role;
		this.memo = memo;
	}

	public boolean isAllPeriods() {
		return periods.isEmpty();
	}

	public boolean covers(LocalDate date) {
		return !date.isBefore(startDate) && !date.isAfter(endDate);
	}

	public boolean covers(LocalDate date, int period) {
		return covers(date) && (isAllPeriods() || periods.contains(period));
	}
}
