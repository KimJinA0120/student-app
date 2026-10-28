package com.center.app.instructor;

import java.time.DayOfWeek;

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

// 요일·교시별 기본 배치
@Entity
@Table(name = "instructor_slot",
	uniqueConstraints = @UniqueConstraint(name = "uq_slot", columnNames = {"instructor_id", "day_of_week", "period"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorSlot extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // FK에 ON DELETE CASCADE 생성
	private Instructor instructor;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, columnDefinition = "varchar(10)")
	private DayOfWeek dayOfWeek;

	// @Min/@Max는 Hibernate가 DDL의 CHECK 제약으로도 만들어 줌
	@Min(1)
	@Max(4)
	@Column(nullable = false)
	private int period;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(10)")
	private InstructorRole role;

	@Column(length = 200)
	private String note;

	InstructorSlot(Instructor instructor, DayOfWeek dayOfWeek, int period, InstructorRole role, String note) {
		this.instructor = instructor;
		this.dayOfWeek = dayOfWeek;
		this.period = period;
		this.role = role;
		this.note = note;
	}
}
