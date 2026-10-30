package com.center.app.lesson;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.center.app.common.BaseEntity;
import com.center.app.instructor.Instructor;
import com.center.app.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

// 예약: 강사 시간표 칸(강사·날짜·교시)에 미리 적어 두는 한 줄 ("김철수, 이영희" / "3명")
// 화면 명칭: content = "메모 예약"(미등록·이름 모르는 수강생도 적는 자유 글), students = "예약"(등록 수강생, [수업시작] 폼에 미리 체크)
// 수업 기록·방문 회차와는 별개
// 강사가 없으면(null) 미배정 예약: 강사 없이 날짜·교시만 정해 오는 수강생. [배정]하면 강사가 채워짐
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_reservation",
	columnNames = {"instructor_id", "reservation_date", "period"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 강사를 지우면 예약도 함께 지움 (기록이 아니라 메모라서). null = 미배정
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "instructor_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Instructor instructor;

	@Column(name = "reservation_date", nullable = false)
	private LocalDate reservationDate;

	@Min(1)
	@Max(4)
	@Column(nullable = false)
	private int period;

	// 글이 없고 수강생만 고른 예약은 "" (빈 문자열)
	@Column(nullable = false, length = 100)
	private String content;

	// 등록된 수강생. 예약을 지우면 Hibernate가 reservation_student 행을 먼저 지움
	@ManyToMany
	@JoinTable(name = "reservation_student",
		joinColumns = @JoinColumn(name = "reservation_id"),
		inverseJoinColumns = @JoinColumn(name = "student_id"),
		indexes = @Index(name = "idx_reservation_student_student", columnList = "student_id"))
	private Set<Student> students = new LinkedHashSet<>();

	public Reservation(Instructor instructor, LocalDate reservationDate, int period, String content) {
		this.instructor = instructor;
		this.reservationDate = reservationDate;
		this.period = period;
		this.content = content;
	}

	public void change(String content) {
		this.content = content;
	}

	public void assign(Instructor instructor) {
		this.instructor = instructor;
	}

	public void changeStudents(Collection<Student> students) {
		this.students.clear();
		this.students.addAll(students);
	}

	public void addStudents(Collection<Student> students) {
		this.students.addAll(students);
	}

	public void removeStudent(Student student) {
		this.students.remove(student);
	}

	// 글도 수강생도 없으면 빈 예약 → 행을 지움
	public boolean isEmpty() {
		return content.isEmpty() && students.isEmpty();
	}
}
