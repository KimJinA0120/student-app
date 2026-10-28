package com.center.app.manager;

import java.time.LocalDate;
import java.time.LocalTime;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 날짜별 예외 (휴무 / 시간 변경). 매니저·날짜당 1행
@Entity
@Table(name = "manager_exception",
	uniqueConstraints = @UniqueConstraint(name = "uq_manager_exception", columnNames = {"manager_id", "exception_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagerException extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "manager_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Manager manager;

	@Column(name = "exception_date", nullable = false)
	private LocalDate exceptionDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private ManagerExceptionType type;

	// TIME_CHANGE일 때만 (서비스에서 필수 검증)
	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(length = 500)
	private String memo;

	public ManagerException(Manager manager, LocalDate exceptionDate, ManagerExceptionType type, LocalTime startTime,
			LocalTime endTime, String memo) {
		this.manager = manager;
		this.exceptionDate = exceptionDate;
		this.type = type;
		this.startTime = startTime;
		this.endTime = endTime;
		this.memo = memo;
	}
}
