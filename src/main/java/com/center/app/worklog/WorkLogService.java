package com.center.app.worklog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.center.app.lesson.Lesson;
import com.center.app.lesson.LessonRepository;
import com.center.app.student.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.optional;

// 업무일지 등록/수정/삭제와 목록, 수업 기록으로 숫자 미리 채우기
@Service
@RequiredArgsConstructor
@Transactional
public class WorkLogService {

	private final WorkLogRepository repository;
	private final LessonRepository lessonRepository;

	// 목록 맨 위 누적 합계 (엑셀 3행). 저장하지 않고 목록에서 계산
	public record Totals(int lessons, int newCount, int existingCount, int students, int days) {
	}

	// 최근 날짜부터
	@Transactional(readOnly = true)
	public List<WorkLog> list() {
		return repository.findAllByOrderByLogDateDesc();
	}

	public static Totals totals(List<WorkLog> logs) {
		return new Totals(
			logs.stream().mapToInt(l -> zero(l.lessonTotal())).sum(),
			logs.stream().mapToInt(l -> zero(l.getNewCount())).sum(),
			logs.stream().mapToInt(l -> zero(l.getExistingCount())).sum(),
			logs.stream().mapToInt(l -> zero(l.studentTotal())).sum(),
			logs.size());
	}

	@Transactional(readOnly = true)
	public WorkLog get(Long id) {
		return find(id);
	}

	@Transactional(readOnly = true)
	public Optional<WorkLog> findByDate(LocalDate date) {
		return repository.findByLogDate(date);
	}

	// 새 업무일지 폼에 미리 넣을 값: 그날 수업 기록에서 계산 (수업 기록이 하나도 없으면 숫자는 빈 칸)
	// 강좌 수 = 교시별 수업 기록 수
	// 신규 = 그날 수업을 들은 수강생 중 첫 수업이 그날인 사람, 기존 = 나머지 (여러 교시를 들어도 1명)
	@Transactional(readOnly = true)
	public WorkLog.Info prefill(LocalDate date) {
		List<Lesson> lessons = lessonRepository.findWithDetailsByDate(date);
		if (lessons.isEmpty()) {
			return new WorkLog.Info(date, null, null, null, null, null, null, null, null);
		}
		int[] periods = new int[5];
		lessons.forEach(l -> periods[l.getPeriod()]++);
		Set<Long> studentIds = lessons.stream()
			.flatMap(l -> l.getStudents().stream())
			.map(Student::getId)
			.collect(Collectors.toSet());
		int newCount = 0;
		if (!studentIds.isEmpty()) {
			Map<Long, LocalDate> firstDates = lessonRepository.findFirstLessonDates(studentIds).stream()
				.collect(Collectors.toMap(r -> (Long) r[0], r -> (LocalDate) r[1]));
			newCount = (int) firstDates.values().stream().filter(date::equals).count();
		}
		return new WorkLog.Info(date, periods[1], periods[2], periods[3], periods[4],
			newCount, studentIds.size() - newCount, null, null);
	}

	public Long create(WorkLog.Info info) {
		WorkLog.Info valid = valid(info);
		checkDateFree(valid.logDate(), null);
		return repository.save(new WorkLog(valid)).getId();
	}

	public void update(Long id, WorkLog.Info info) {
		WorkLog.Info valid = valid(info);
		checkDateFree(valid.logDate(), id);
		find(id).change(valid);
	}

	public void delete(Long id) {
		repository.delete(find(id));
	}

	@Transactional(readOnly = true)
	public boolean isEmpty() {
		return repository.count() == 0;
	}

	// 엑셀 첫 이전: 한 건이라도 입력 오류가 있으면 전부 취소 (반쯤 들어간 상태로 남지 않도록)
	// 그사이 누가 먼저 적었으면 넣지 않음 (업무일지가 비어 있을 때만 = 처음 한 번만)
	public int importAll(List<WorkLog.Info> rows) {
		if (!isEmpty()) {
			return 0;
		}
		for (WorkLog.Info row : rows) {
			try {
				create(row);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(row.logDate() + " 업무일지: " + e.getMessage(), e);
			}
		}
		return rows.size();
	}

	// 하루에 한 줄. DB UNIQUE 제약도 있지만 먼저 알아보기 쉬운 메시지로 알림
	private void checkDateFree(LocalDate date, Long selfId) {
		repository.findByLogDate(date)
			.filter(other -> !other.getId().equals(selfId))
			.ifPresent(other -> {
				throw new IllegalArgumentException(date.getMonthValue() + "/" + date.getDayOfMonth()
					+ " 업무일지가 이미 있습니다. 목록에서 그 날짜를 눌러 수정하세요.");
			});
	}

	private WorkLog find(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("업무일지를 찾을 수 없습니다: " + id));
	}

	private WorkLog.Info valid(WorkLog.Info in) {
		if (in.logDate() == null) {
			throw new IllegalArgumentException("날짜를 입력하세요.");
		}
		return new WorkLog.Info(
			in.logDate(),
			count(in.period1(), "1교시 강좌 수"),
			count(in.period2(), "2교시 강좌 수"),
			count(in.period3(), "3교시 강좌 수"),
			count(in.period4(), "4교시 강좌 수"),
			count(in.newCount(), "신규 수강생 수"),
			count(in.existingCount(), "기존 수강생 수"),
			optional(in.instructorNote(), 1000, "강사"),
			optional(in.specialNote(), 2000, "특이사항"));
	}

	private static Integer count(Integer value, String label) {
		if (value != null && (value < 0 || value > 999)) {
			throw new IllegalArgumentException(label + "는 0~999 사이로 입력하세요.");
		}
		return value;
	}

	private static int zero(Integer value) {
		return value == null ? 0 : value;
	}
}
