package com.center.app.student;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.lesson.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.optional;
import static com.center.app.common.Inputs.required;

// 수강생 등록/수정/삭제와 목록·상세 조회
@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {

	private final StudentRepository studentRepository;
	private final InstructorRepository instructorRepository;
	private final LessonRepository lessonRepository;
	private final ReservationService reservationService;

	// 상세 화면의 수업 한 줄
	public record LessonLine(Long lessonId, LocalDate date, int period, String instructorName) {
	}

	// 이름에 keyword가 들어간 수강생 (빈 값이면 전체), 이름순
	@Transactional(readOnly = true)
	public List<StudentRow> list(String keyword) {
		String q = keyword == null ? "" : keyword.trim();
		Map<Long, Long> visits = visitCounts();
		return studentRepository.findAllWithHopes().stream()
			.filter(s -> q.isEmpty() || s.getName().contains(q))
			.map(s -> {
				List<Instructor> hopes = s.getHopeInstructors().stream()
					.sorted(Comparator.comparing(Instructor::getName)).toList();
				return new StudentRow(s.getId(), s.getName(), visits.getOrDefault(s.getId(), 0L),
					hopes.stream().map(Instructor::getName).toList(), hopes.stream().map(Instructor::getId).toList());
			})
			.toList();
	}

	@Transactional(readOnly = true)
	public Student get(Long id) {
		return studentRepository.findWithHopesById(id)
			.orElseThrow(() -> new IllegalArgumentException("수강생을 찾을 수 없습니다: " + id));
	}

	// 수업 기록 폼에 미리 넣을 수강생들 (없는 id는 무시)
	@Transactional(readOnly = true)
	public List<Student> getAll(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return studentRepository.findAllWithHopesByIdIn(ids).stream()
			.sorted(Comparator.comparing(Student::getName)).toList();
	}

	@Transactional(readOnly = true)
	public long visitCount(Long id) {
		return lessonRepository.countByStudentId(id);
	}

	@Transactional(readOnly = true)
	public List<LessonLine> lessonsOf(Long id) {
		return lessonRepository.findByStudentIdOrderByRecent(id).stream()
			.map(l -> new LessonLine(l.getId(), l.getLessonDate(), l.getPeriod(), l.getInstructor().getName()))
			.toList();
	}

	public Long create(Student.Info info, Set<Long> hopeInstructorIds) {
		return studentRepository.save(new Student(valid(info), hopes(hopeInstructorIds))).getId();
	}

	public void update(Long id, Student.Info info, Set<Long> hopeInstructorIds) {
		find(id).change(valid(info), hopes(hopeInstructorIds));
	}

	// 방문 회차·수업 기록을 지키기 위해 수업 기록이 있으면 삭제하지 않음
	public void delete(Long id) {
		Student student = find(id);
		if (lessonRepository.countByStudentId(id) > 0) {
			throw new IllegalArgumentException("수업 기록이 있는 수강생은 삭제할 수 없습니다. 수업 기록을 먼저 정리하세요.");
		}
		reservationService.removeStudent(student);
		studentRepository.delete(student);
	}

	private Map<Long, Long> visitCounts() {
		return lessonRepository.countByStudent().stream()
			.collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
	}

	private Student find(Long id) {
		return studentRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("수강생을 찾을 수 없습니다: " + id));
	}

	private Set<Instructor> hopes(Set<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		List<Instructor> found = instructorRepository.findAllById(ids);
		if (found.size() != ids.size()) {
			throw new IllegalArgumentException("희망 강사를 찾을 수 없습니다. 화면을 새로고침하세요.");
		}
		return new HashSet<>(found);
	}

	private Student.Info valid(Student.Info in) {
		if (in.age() != null && (in.age() < 0 || in.age() > 150)) {
			throw new IllegalArgumentException("나이를 확인하세요.");
		}
		return new Student.Info(
			required(in.name(), 30, "성명"),
			optional(in.phone(), 20, "연락처"),
			in.age(),
			optional(in.hopeClass(), 100, "희망 수업"),
			optional(in.attendId(), 50, "ID"),
			in.startDate(),
			optional(in.memo(), 2000, "비고"));
	}
}
