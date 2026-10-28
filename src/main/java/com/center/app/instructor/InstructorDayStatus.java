package com.center.app.instructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.center.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 당일 상태 (휴식 / 외출 / 조퇴). 강사·날짜당 1행, [복귀] 시 행 삭제
@Entity
@Table(name = "instructor_day_status",
	uniqueConstraints = @UniqueConstraint(name = "uq_day_status", columnNames = {"instructor_id", "status_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorDayStatus extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Instructor instructor;

	@Column(name = "status_date", nullable = false)
	private LocalDate statusDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private DayStatus status;

	// 상태가 시작된 교시. 조퇴면 이 교시까지 "조퇴" 표시, 이후 교시는 근무에서 제외
	@Min(1)
	@Max(4)
	@Column(name = "from_period", nullable = false)
	private int fromPeriod;

	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;

	public InstructorDayStatus(Instructor instructor, LocalDate statusDate, DayStatus status, int fromPeriod,
			LocalDateTime changedAt) {
		this.instructor = instructor;
		this.statusDate = statusDate;
		this.status = status;
		this.fromPeriod = fromPeriod;
		this.changedAt = changedAt;
	}

	// 휴식 중 조퇴 등: 같은 행을 새 상태로 갱신
	public void change(DayStatus status, int fromPeriod, LocalDateTime changedAt) {
		this.status = status;
		this.fromPeriod = fromPeriod;
		this.changedAt = changedAt;
	}

	public boolean excludes(int period) {
		return status == DayStatus.LEAVE_EARLY && fromPeriod < period;
	}
}
