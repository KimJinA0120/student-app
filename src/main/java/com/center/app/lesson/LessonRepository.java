package com.center.app.lesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

	// 화면에 필요한 연관(강사·교실·수강생)을 한 번에 가져옴
	@Query("""
		select distinct l from Lesson l join fetch l.instructor join fetch l.room left join fetch l.students
		order by l.lessonDate desc, l.period desc, l.id desc""")
	List<Lesson> findAllWithDetails();

	@Query("""
		select distinct l from Lesson l join fetch l.instructor join fetch l.room left join fetch l.students
		where l.lessonDate = :date and l.period = :period""")
	List<Lesson> findWithDetailsByDateAndPeriod(LocalDate date, int period);

	// 강사 시간표: 하루치 수업 (교시 → 등록 순)
	@Query("""
		select distinct l from Lesson l join fetch l.instructor join fetch l.room left join fetch l.students
		where l.lessonDate = :date order by l.period, l.id""")
	List<Lesson> findWithDetailsByDate(LocalDate date);

	@Query("""
		select l from Lesson l join fetch l.instructor join fetch l.room left join fetch l.students
		where l.id = :id""")
	Optional<Lesson> findWithDetailsById(Long id);

	// 한 수강생이 들은 수업 (최근순)
	@Query("""
		select l from Lesson l join fetch l.instructor join l.students s
		where s.id = :studentId order by l.lessonDate desc, l.period desc, l.id desc""")
	List<Lesson> findByStudentIdOrderByRecent(Long studentId);

	// 방문 회차 = 수강생별 lesson_student 행 수 → [수강생 id, 횟수]
	@Query("select s.id, count(l) from Lesson l join l.students s group by s.id")
	List<Object[]> countByStudent();

	@Query("select count(l) from Lesson l join l.students s where s.id = :studentId")
	long countByStudentId(Long studentId);

	boolean existsByInstructorId(Long instructorId);
}
