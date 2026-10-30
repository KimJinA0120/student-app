package com.center.app.instructor;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.center.app.common.Inputs;
import com.center.app.common.PeriodUtil;
import com.center.app.lesson.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.blankToNull;
import static com.center.app.common.Inputs.limit;

// 강사 정보·기본 배치·날짜별 예외 등록/수정/삭제
// 입력 오류는 IllegalArgumentException(한국어 메시지)으로 알리고, 컨트롤러가 화면에 보여 줌
@Service
@RequiredArgsConstructor
@Transactional
public class InstructorService {

	private final InstructorRepository instructorRepository;
	private final InstructorOverrideRepository overrideRepository;
	private final LessonRepository lessonRepository;
	private final InstructorDayStatusRepository dayStatusRepository;
	private final Clock clock;

	// 기본 배치 한 칸 (요일·교시·역할·비고)
	public record SlotInput(DayOfWeek dayOfWeek, int period, InstructorRole role, String note) {
	}

	@Transactional(readOnly = true)
	public List<Instructor> findAll() {
		return instructorRepository.findAllByOrderByName();
	}

	// 기본 배치까지 함께 읽음 (open-in-view=false라 화면에서 지연 로딩 불가)
	@Transactional(readOnly = true)
	public Instructor get(Long id) {
		return instructorRepository.findWithSlotsById(id)
			.orElseThrow(() -> new IllegalArgumentException("강사를 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public List<InstructorOverride> overridesOf(Long instructorId) {
		return overrideRepository.findByInstructorIdOrderByStartDateAscTypeAsc(instructorId);
	}

	public Long create(String name, String phone) {
		return instructorRepository.save(new Instructor(validName(name), validPhone(phone))).getId();
	}

	public void update(Long id, String name, String phone) {
		find(id).changeInfo(validName(name), validPhone(phone));
	}

	// 기본 배치는 엔티티 cascade로, 예외·당일 상태는 DB의 ON DELETE CASCADE로 함께 삭제됨
	// 수업 기록은 남겨야 하므로 기록이 있는 강사는 삭제하지 않음
	public void delete(Long id) {
		Instructor instructor = find(id);
		if (lessonRepository.existsByInstructorId(id)) {
			throw new IllegalArgumentException("수업 기록이 있는 강사는 삭제할 수 없습니다. 수업 기록을 먼저 정리하세요.");
		}
		instructorRepository.deleteHopeLinks(id);
		instructorRepository.delete(instructor);
	}

	// 기본 배치 전체를 화면에서 받은 내용으로 교체
	public void replaceSlots(Long id, List<SlotInput> slots) {
		Instructor instructor = find(id);
		instructor.clearSlots();
		// Hibernate는 flush할 때 INSERT를 DELETE보다 먼저 실행함
		// → 같은 (강사, 요일, 교시)를 지우고 다시 넣으면 유니크 제약 위반이 나므로, 삭제를 먼저 DB에 반영
		instructorRepository.flush();
		for (SlotInput s : slots) {
			if (!PeriodUtil.isValid(s.period())) {
				throw new IllegalArgumentException("교시는 1~4만 가능합니다: " + s.period());
			}
			instructor.addSlot(s.dayOfWeek(), s.period(), s.role(), limit(blankToNull(s.note()), 200, "비고"));
		}
	}

	// 종료일을 비우면 시작일 하루짜리, 교시를 비우면 전 교시
	public void addOverride(Long instructorId, OverrideType type, LocalDate startDate, LocalDate endDate,
			Set<Integer> periods, InstructorRole role, String memo) {
		if (type == null) {
			throw new IllegalArgumentException("종류를 선택하세요.");
		}
		if (startDate == null) {
			throw new IllegalArgumentException("시작일을 입력하세요.");
		}
		if (periods != null && !periods.stream().allMatch(PeriodUtil::isValid)) {
			throw new IllegalArgumentException("교시는 1~4만 가능합니다.");
		}
		String cleanMemo = limit(blankToNull(memo), 500, "메모");
		if (type == OverrideType.NOTE && cleanMemo == null) {
			throw new IllegalArgumentException("메모 종류는 내용을 입력하세요.");
		}
		// 역할은 추가 배치일 때만 의미가 있음
		InstructorRole extraRole = type == OverrideType.EXTRA ? role : null;
		overrideRepository.save(new InstructorOverride(find(instructorId), type, startDate,
			endDate == null ? startDate : endDate, periods, extraRole, cleanMemo));
	}

	public void deleteOverride(Long instructorId, Long overrideId) {
		InstructorOverride o = overrideRepository.findById(overrideId)
			.filter(found -> found.getInstructor().getId().equals(instructorId))
			.orElseThrow(() -> new IllegalArgumentException("예외를 찾을 수 없습니다: " + overrideId));
		overrideRepository.delete(o);
	}

	// 조퇴 (강사 시간표 칸의 버튼): fromPeriod 교시까지 근무하고 이후 교시에서 빠짐
	public void leaveEarly(Long instructorId, LocalDate date, Integer fromPeriod) {
		if (date == null || fromPeriod == null || !PeriodUtil.isValid(fromPeriod)) {
			throw new IllegalArgumentException("날짜·교시를 확인하세요.");
		}
		Instructor instructor = find(instructorId);
		if (dayStatusRepository.findByInstructorIdAndStatusDate(instructorId, date).isPresent()) {
			throw new IllegalArgumentException(instructor.getName() + " 강사님은 이미 조퇴 처리되었습니다.");
		}
		dayStatusRepository.save(new InstructorDayStatus(instructor, date, DayStatus.LEAVE_EARLY, fromPeriod,
			LocalDateTime.now(clock)));
	}

	// 조퇴 [취소]: 잘못 눌렀을 때 행을 지워 원래 근무로 되돌림
	public void cancelLeaveEarly(Long instructorId, LocalDate date) {
		InstructorDayStatus s = dayStatusRepository.findByInstructorIdAndStatusDate(instructorId, date)
			.orElseThrow(() -> new IllegalArgumentException("조퇴 기록이 없습니다."));
		dayStatusRepository.delete(s);
	}

	private Instructor find(Long id) {
		return instructorRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("강사를 찾을 수 없습니다: " + id));
	}

	private String validName(String name) {
		String trimmed = blankToNull(name);
		if (trimmed == null) {
			throw new IllegalArgumentException("이름을 입력하세요.");
		}
		return limit(trimmed, 30, "이름");
	}

	private String validPhone(String phone) {
		return Inputs.optional(phone, 20, "연락처");
	}
}
