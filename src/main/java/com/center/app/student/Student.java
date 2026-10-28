package com.center.app.student;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.center.app.common.BaseEntity;
import com.center.app.instructor.Instructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(indexes = @Index(name = "idx_student_name", columnList = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 20)
	private String phone;

	private Integer age;

	// 희망 수업 (예: 스마트폰 기초)
	@Column(name = "hope_class", length = 100)
	private String hopeClass;

	// 출석 사이트 ID (기록만)
	@Column(name = "attend_id", length = 50)
	private String attendId;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(length = 2000)
	private String memo;

	// 희망 강사 (선택, 여러 명). 강사를 삭제할 때는 InstructorService가 이 연결 행을 먼저 지움
	@ManyToMany
	@JoinTable(name = "student_hope_instructor",
		joinColumns = @JoinColumn(name = "student_id"),
		inverseJoinColumns = @JoinColumn(name = "instructor_id"))
	private Set<Instructor> hopeInstructors = new LinkedHashSet<>();

	public Student(Info info, Set<Instructor> hopeInstructors) {
		change(info, hopeInstructors);
	}

	public void change(Info info, Set<Instructor> hopeInstructors) {
		this.name = info.name();
		this.phone = info.phone();
		this.age = info.age();
		this.hopeClass = info.hopeClass();
		this.attendId = info.attendId();
		this.startDate = info.startDate();
		this.memo = info.memo();
		this.hopeInstructors.clear();
		this.hopeInstructors.addAll(hopeInstructors);
	}

	// 희망 강사를 뺀 기본 정보 (등록·수정 폼에서 그대로 받음)
	public record Info(String name, String phone, Integer age, String hopeClass, String attendId,
			LocalDate startDate, String memo) {
	}
}
