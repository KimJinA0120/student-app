package com.center.app.lesson;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.center.app.common.BaseEntity;
import com.center.app.common.PeriodUtil;
import com.center.app.instructor.Instructor;
import com.center.app.room.Room;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 수업 = 강사 1명 + 수강생 1명 이상. 강사·교실·수강생 쪽 FK에는 CASCADE를 두지 않음 (기록이 있으면 삭제 불가)
@Entity
@Table(indexes = {
	@Index(name = "idx_lesson_date_period", columnList = "lesson_date, period"),
	@Index(name = "idx_lesson_instructor", columnList = "instructor_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "lesson_date", nullable = false)
	private LocalDate lessonDate;

	@Min(1)
	@Max(4)
	@Column(nullable = false)
	private int period;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	private Instructor instructor;

	// NULL = 교시 시작 시각
	@Column(name = "start_time")
	private LocalTime startTime;

	// NULL = 교시 종료 시각. [종료]를 누르면 그때 시각을 기록
	@Column(name = "end_time")
	private LocalTime endTime;

	// 수업을 지우면 Hibernate가 lesson_student 행을 먼저 지움
	@ManyToMany
	@JoinTable(name = "lesson_student",
		joinColumns = @JoinColumn(name = "lesson_id"),
		inverseJoinColumns = @JoinColumn(name = "student_id"),
		indexes = @Index(name = "idx_lesson_student_student", columnList = "student_id"))
	private Set<Student> students = new LinkedHashSet<>();

	public Lesson(LocalDate lessonDate, int period, Room room, Instructor instructor, LocalTime startTime,
			LocalTime endTime, Set<Student> students) {
		change(lessonDate, period, room, instructor, startTime, endTime, students);
	}

	public void change(LocalDate lessonDate, int period, Room room, Instructor instructor, LocalTime startTime,
			LocalTime endTime, Set<Student> students) {
		this.lessonDate = lessonDate;
		this.period = period;
		this.room = room;
		this.instructor = instructor;
		this.startTime = startTime;
		this.endTime = endTime;
		this.students.clear();
		this.students.addAll(students);
	}

	public void addStudents(Set<Student> added) {
		students.addAll(added);
	}

	public void end(LocalTime time) {
		this.endTime = time;
	}

	// 비어 있으면 교시 시각으로 봄
	public LocalTime effectiveStart() {
		return startTime != null ? startTime : PeriodUtil.start(period);
	}

	public LocalTime effectiveEnd() {
		return endTime != null ? endTime : PeriodUtil.end(period);
	}

	// 진행 중 = 기준 시각 < coalesce(end_time, 교시 종료). 교시가 끝나면 자동으로 종료로 간주
	public boolean isOngoingAt(LocalDate date, int period, LocalTime time) {
		return lessonDate.equals(date) && this.period == period && time.isBefore(effectiveEnd());
	}
}
