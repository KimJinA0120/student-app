package com.center.app.lesson;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.room.Room;
import com.center.app.room.RoomRepository;
import com.center.app.student.Student;
import com.center.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 수업 기록 저장·수정·삭제·종료·수강생 추가와 검증
// 강사·교실 중복은 DB 제약이 아니라 여기서 검사 (같은 교시 안에서도 중간 종료 후 새 수업이 시작될 수 있음)
@Service
@RequiredArgsConstructor
@Transactional
public class LessonService {

	private final LessonRepository lessonRepository;
	private final InstructorRepository instructorRepository;
	private final RoomRepository roomRepository;
	private final StudentRepository studentRepository;
	private final Clock clock;

	// 폼 입력. 시작·종료 시각은 선택 (비우면 교시 시각)
	public record LessonInput(LocalDate date, Integer period, Long roomId, Long instructorId,
			LocalTime startTime, LocalTime endTime, Set<Long> studentIds) {
	}

	// 저장 결과. 지난 기록에서 강사·교실이 겹치면 막지 않고 warning으로 알림
	public record SaveResult(Long id, String warning) {
	}

	// 수강생·강사 이름 검색 (빈 값이면 전체), 최근 날짜순
	@Transactional(readOnly = true)
	public List<LessonRow> list(String keyword) {
		String q = keyword == null ? "" : keyword.trim();
		return lessonRepository.findAllWithDetails().stream()
			.filter(l -> q.isEmpty() || l.getInstructor().getName().contains(q)
				|| l.getStudents().stream().anyMatch(s -> s.getName().contains(q)))
			.map(LessonService::toRow)
			.toList();
	}

	// 강사·교실·수강생까지 읽음 (open-in-view=false라 화면에서 지연 로딩 불가)
	@Transactional(readOnly = true)
	public Lesson get(Long id) {
		return lessonRepository.findWithDetailsById(id)
			.orElseThrow(() -> new IllegalArgumentException("수업 기록을 찾을 수 없습니다: " + id));
	}

	public SaveResult create(LessonInput input) {
		Prepared p = prepare(input, null);
		Lesson lesson = lessonRepository.save(new Lesson(input.date(), input.period(), p.room, p.instructor,
			p.start, p.end, p.students));
		return new SaveResult(lesson.getId(), p.warning);
	}

	public SaveResult update(Long id, LessonInput input) {
		Lesson lesson = get(id);
		Prepared p = prepare(input, id);
		lesson.change(input.date(), input.period(), p.room, p.instructor, p.start, p.end, p.students);
		return new SaveResult(id, p.warning);
	}

	public void delete(Long id) {
		lessonRepository.delete(get(id));
	}

	// [추가]: 진행 중 수업에 수강생 추가 (이미 들어 있는 수강생은 그대로)
	public void addStudents(Long id, Set<Long> studentIds) {
		if (studentIds == null || studentIds.isEmpty()) {
			throw new IllegalArgumentException("추가할 수강생을 선택하세요.");
		}
		get(id).addStudents(students(studentIds));
	}

	// [종료]: 교시 중간에 끝난 수업의 종료 시각을 지금으로 기록
	public void end(Long id) {
		Lesson lesson = get(id);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
		if (!lesson.getLessonDate().equals(now.toLocalDate()) || !time.isBefore(lesson.effectiveEnd())) {
			throw new IllegalArgumentException("이미 끝난 수업입니다. 지난 수업의 시각은 수업 기록 탭에서 수정하세요.");
		}
		if (!time.isAfter(lesson.effectiveStart())) {
			throw new IllegalArgumentException("아직 시작하지 않은 수업입니다. 잘못 입력했다면 [취소]하세요.");
		}
		lesson.end(time);
	}

	private record Prepared(Room room, Instructor instructor, LocalTime start, LocalTime end, Set<Student> students,
			String warning) {
	}

	private Prepared prepare(LessonInput in, Long selfId) {
		if (in.studentIds() == null || in.studentIds().isEmpty()) {
			throw new IllegalArgumentException("수강생을 1명 이상 선택하세요.");
		}
		if (in.instructorId() == null) {
			throw new IllegalArgumentException("강사를 선택하세요.");
		}
		if (in.roomId() == null) {
			throw new IllegalArgumentException("강의실을 선택하세요.");
		}
		if (in.date() == null) {
			throw new IllegalArgumentException("날짜를 입력하세요.");
		}
		if (in.period() == null || !PeriodUtil.isValid(in.period())) {
			throw new IllegalArgumentException("교시를 선택하세요.");
		}
		int period = in.period();
		LocalTime start = minutes(in.startTime());
		LocalTime end = minutes(in.endTime());
		validateTimes(period, start, end);

		Room room = roomRepository.findById(in.roomId())
			.orElseThrow(() -> new IllegalArgumentException("강의실을 찾을 수 없습니다."));
		Instructor instructor = instructorRepository.findById(in.instructorId())
			.orElseThrow(() -> new IllegalArgumentException("강사를 찾을 수 없습니다."));
		Set<Student> students = students(in.studentIds());

		LocalDateTime now = LocalDateTime.now(clock);
		List<String> conflicts = conflicts(in.date(), period, start, end, room, instructor, selfId, now);
		if (conflicts.isEmpty()) {
			return new Prepared(room, instructor, start, end, students, null);
		}
		String message = String.join(" ", conflicts);
		if (!RefPoint.isPast(in.date(), period, now)) {
			throw new IllegalArgumentException(message);
		}
		// 지난 기록(수기 기록 이전)은 유연하게: 저장하고 경고만
		return new Prepared(room, instructor, start, end, students, "저장했지만 확인하세요: " + message);
	}

	// 둘 다 있으면 시작 < 종료, 각각 교시 시간 범위 안
	private void validateTimes(int period, LocalTime start, LocalTime end) {
		LocalTime ps = PeriodUtil.start(period);
		LocalTime pe = PeriodUtil.end(period);
		String range = " (" + period + "교시 " + ps + "~" + pe + ")";
		if (start != null && (start.isBefore(ps) || !start.isBefore(pe))) {
			throw new IllegalArgumentException("시작 시각이 교시 시간 밖입니다." + range);
		}
		if (end != null && (!end.isAfter(ps) || end.isAfter(pe))) {
			throw new IllegalArgumentException("종료 시각이 교시 시간 밖입니다." + range);
		}
		if (start != null && end != null && !start.isBefore(end)) {
			throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");
		}
	}

	// 같은 날짜·교시의 다른 수업 중 시간이 겹치면서 같은 강사 또는 같은 교실을 쓰는 수업
	// 오늘 수업은 지금 이후만 봄: 중간에 [종료]한 수업 뒤에 새 수업을 시작해도 겹치지 않게
	private List<String> conflicts(LocalDate date, int period, LocalTime start, LocalTime end, Room room,
			Instructor instructor, Long selfId, LocalDateTime now) {
		LocalTime from = start != null ? start : PeriodUtil.start(period);
		LocalTime to = end != null ? end : PeriodUtil.end(period);
		if (date.equals(now.toLocalDate()) && !RefPoint.isPast(date, period, now)) {
			LocalTime nowTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
			if (from.isBefore(nowTime)) {
				from = nowTime;
			}
		}
		List<String> messages = new ArrayList<>();
		for (Lesson other : lessonRepository.findWithDetailsByDateAndPeriod(date, period)) {
			if (Objects.equals(other.getId(), selfId)
				|| !(other.effectiveStart().isBefore(to) && from.isBefore(other.effectiveEnd()))) {
				continue;
			}
			if (other.getInstructor().getId().equals(instructor.getId())) {
				messages.add(instructor.getName() + " 강사님은 같은 시간에 " + other.getRoom().getName()
					+ "에서 수업 중입니다.");
			}
			if (other.getRoom().getId().equals(room.getId())) {
				messages.add(room.getName() + "은(는) 같은 시간에 " + other.getInstructor().getName()
					+ " 강사님 수업이 있습니다.");
			}
		}
		return messages;
	}

	private Set<Student> students(Set<Long> ids) {
		List<Student> found = studentRepository.findAllById(ids);
		if (found.size() != ids.size()) {
			throw new IllegalArgumentException("수강생을 찾을 수 없습니다. 화면을 새로고침하세요.");
		}
		return new HashSet<>(found);
	}

	private static LocalTime minutes(LocalTime time) {
		return time == null ? null : time.truncatedTo(ChronoUnit.MINUTES);
	}

	private static LessonRow toRow(Lesson l) {
		return new LessonRow(l.getId(), l.getLessonDate(), l.getPeriod(), l.getRoom().getName(),
			l.getInstructor().getName(), l.getStudents().stream().map(Student::getName).sorted().toList(),
			l.getStartTime(), l.getEndTime());
	}
}
