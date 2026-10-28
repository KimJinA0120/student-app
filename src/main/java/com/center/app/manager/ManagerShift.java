package com.center.app.manager;

import java.time.DayOfWeek;
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

// 요일별 기본 근무 시간
@Entity
@Table(name = "manager_shift",
	uniqueConstraints = @UniqueConstraint(name = "uq_manager_shift", columnNames = {"manager_id", "day_of_week"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagerShift extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "manager_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Manager manager;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, columnDefinition = "varchar(10)")
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	ManagerShift(Manager manager, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		this.manager = manager;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
	}
}
