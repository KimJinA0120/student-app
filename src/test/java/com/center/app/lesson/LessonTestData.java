package com.center.app.lesson;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.instructor.InstructorRole;
import com.center.app.room.Room;
import com.center.app.room.RoomRepository;
import com.center.app.student.Student;
import com.center.app.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 수업 관련 테스트 공통 데이터: 월 2교시 근무 김강사(주)·이강사(보조), 근무 없는 최강사, 수강생 2명
public class LessonTestData {

	public static final LocalDate MON = LocalDate.of(2026, 11, 2);

	public Instructor kim;
	public Instructor lee;
	public Instructor choi;
	public Student hong;
	public Student park;
	public List<Room> rooms;

	@Component
	public static class Factory {

		@Autowired
		LessonRepository lessonRepository;

		@Autowired
		StudentRepository studentRepository;

		@Autowired
		InstructorRepository instructorRepository;

		@Autowired
		RoomRepository roomRepository;

		public LessonTestData create() {
			lessonRepository.deleteAll();
			studentRepository.deleteAll();
			instructorRepository.deleteAll();
			LessonTestData d = new LessonTestData();
			d.kim = new Instructor("김강사", null);
			d.kim.addSlot(DayOfWeek.MONDAY, 2, InstructorRole.MAIN, null);
			d.lee = new Instructor("이강사", null);
			d.lee.addSlot(DayOfWeek.MONDAY, 2, InstructorRole.ASSIST, null);
			d.choi = new Instructor("최강사", null);
			instructorRepository.saveAll(List.of(d.kim, d.lee, d.choi));
			d.hong = studentRepository.save(new Student(
				new Student.Info("홍길동", null, null, null, null, null, null), Set.of(d.lee)));
			d.park = studentRepository.save(new Student(
				new Student.Info("박영희", null, null, null, null, null, null), Set.of()));
			d.rooms = roomRepository.findAllByOrderBySortOrderAsc();
			return d;
		}
	}
}
