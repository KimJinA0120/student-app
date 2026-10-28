package com.center.app.manager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.center.app.manager.ManagerDay.Change;
import com.center.app.manager.ManagerDay.Working;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.optional;
import static com.center.app.common.Inputs.required;

// 운영 매니저 정보·기본 근무표·날짜별 예외와 날짜별 근무 계산
@Service
@RequiredArgsConstructor
@Transactional
public class ManagerService {

	private final ManagerRepository managerRepository;
	private final ManagerExceptionRepository exceptionRepository;

	// 기본 근무 한 줄 (요일·시작·종료)
	public record ShiftInput(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
	}

	@Transactional(readOnly = true)
	public List<Manager> findAll() {
		return managerRepository.findAllWithShifts();
	}

	@Transactional(readOnly = true)
	public Manager get(Long id) {
		return managerRepository.findWithShiftsById(id)
			.orElseThrow(() -> new IllegalArgumentException("매니저를 찾을 수 없습니다: " + id));
	}

	@Transactional(readOnly = true)
	public List<ManagerException> exceptionsOf(Long managerId) {
		return exceptionRepository.findByManagerIdOrderByExceptionDate(managerId);
	}

	@Transactional(readOnly = true)
	public ManagerDay day(LocalDate date) {
		Map<Long, ManagerException> exceptions = exceptionRepository.findByExceptionDate(date).stream()
			.collect(Collectors.toMap(e -> e.getManager().getId(), Function.identity()));
		List<Working> working = new ArrayList<>();
		List<Change> changes = new ArrayList<>();
		for (Manager m : managerRepository.findAllWithShifts()) {
			ManagerShift shift = m.shiftOn(date.getDayOfWeek());
			ManagerException ex = exceptions.get(m.getId());
			if (ex != null) {
				changes.add(new Change(m.getId(), m.getName(), ex.getType(), ex.getStartTime(), ex.getEndTime(),
					ex.getMemo()));
				if (ex.getType() == ManagerExceptionType.TIME_CHANGE) {
					working.add(new Working(m.getId(), m.getName(), m.getPhone(), ex.getStartTime(), ex.getEndTime(), true));
				}
			} else if (shift != null) {
				working.add(new Working(m.getId(), m.getName(), m.getPhone(), shift.getStartTime(), shift.getEndTime(),
					false));
			}
		}
		working.sort(Comparator.comparing(Working::startTime).thenComparing(Working::name));
		changes.sort(Comparator.comparing(Change::name));
		return new ManagerDay(date, working, changes);
	}

	public Long create(String name, String phone) {
		return managerRepository.save(new Manager(required(name, 30, "이름"), optional(phone, 20, "연락처"))).getId();
	}

	public void update(Long id, String name, String phone) {
		find(id).changeInfo(required(name, 30, "이름"), optional(phone, 20, "연락처"));
	}

	// 기본 근무표·예외는 함께 삭제됨 (엔티티 cascade / DB의 ON DELETE CASCADE)
	public void delete(Long id) {
		managerRepository.delete(find(id));
	}

	// 기본 근무표 전체를 화면에서 받은 내용으로 교체
	public void replaceShifts(Long id, List<ShiftInput> shifts) {
		for (ShiftInput s : shifts) {
			validRange(s.startTime(), s.endTime(), dayLabel(s.dayOfWeek()) + "요일 ");
		}
		Manager manager = find(id);
		manager.clearShifts();
		// 강사 기본 배치와 같은 이유: 같은 (매니저, 요일)을 지우고 다시 넣을 때 유니크 제약 위반을 피하려고 삭제를 먼저 반영
		managerRepository.flush();
		shifts.forEach(s -> manager.addShift(s.dayOfWeek(), s.startTime(), s.endTime()));
	}

	public void addException(Long managerId, LocalDate date, ManagerExceptionType type, LocalTime startTime,
			LocalTime endTime, String memo) {
		if (date == null) {
			throw new IllegalArgumentException("날짜를 입력하세요.");
		}
		if (type == null) {
			throw new IllegalArgumentException("종류를 선택하세요.");
		}
		if (type == ManagerExceptionType.TIME_CHANGE) {
			if (startTime == null || endTime == null) {
				throw new IllegalArgumentException("시간 변경은 시작·종료 시각을 입력하세요.");
			}
			validRange(startTime, endTime, "");
		}
		Manager manager = find(managerId);
		if (exceptionRepository.existsByManagerIdAndExceptionDate(managerId, date)) {
			throw new IllegalArgumentException("그 날짜에는 이미 예외가 있습니다. 기존 예외를 삭제하고 다시 입력하세요.");
		}
		boolean timeChange = type == ManagerExceptionType.TIME_CHANGE;
		exceptionRepository.save(new ManagerException(manager, date, type, timeChange ? startTime : null,
			timeChange ? endTime : null, optional(memo, 500, "메모")));
	}

	public void deleteException(Long managerId, Long exceptionId) {
		ManagerException e = exceptionRepository.findById(exceptionId)
			.filter(found -> found.getManager().getId().equals(managerId))
			.orElseThrow(() -> new IllegalArgumentException("예외를 찾을 수 없습니다: " + exceptionId));
		exceptionRepository.delete(e);
	}

	private void validRange(LocalTime start, LocalTime end, String prefix) {
		if (start == null || end == null) {
			throw new IllegalArgumentException(prefix + "시작·종료 시각을 모두 입력하세요.");
		}
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException(prefix + "종료 시각은 시작 시각보다 늦어야 합니다.");
		}
	}

	private static String dayLabel(DayOfWeek d) {
		return "월화수목금토일".substring(d.getValue() - 1, d.getValue());
	}

	private Manager find(Long id) {
		return managerRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("매니저를 찾을 수 없습니다: " + id));
	}
}
