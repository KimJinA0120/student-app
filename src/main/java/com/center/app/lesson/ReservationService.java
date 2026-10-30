package com.center.app.lesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.center.app.common.PeriodUtil;
import com.center.app.instructor.DaySchedule.Entry;
import com.center.app.instructor.DaySchedule.PeriodSchedule;
import com.center.app.instructor.Instructor;
import com.center.app.instructor.InstructorRepository;
import com.center.app.instructor.InstructorScheduleService;
import com.center.app.student.Student;
import com.center.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.blankToNull;
import static com.center.app.common.Inputs.limit;

// 예약 저장·삭제·배정. 칸(강사·날짜·교시, 미배정은 강사 없이 날짜·교시)마다 한 줄이라 저장하면 덮어쓰고, 비우면 지움
// 한 행 = 메모 예약(content, 자유 글) + 예약(students, 등록 수강생). 둘 다 비면 행을 지움
@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final InstructorRepository instructorRepository;
	private final InstructorScheduleService scheduleService;
	private final StudentRepository studentRepository;

	@Transactional(readOnly = true)
	public List<Reservation> findByDate(LocalDate date) {
		return reservationRepository.findWithInstructorByDate(date);
	}

	@Transactional(readOnly = true)
	public Optional<Reservation> find(Long instructorId, LocalDate date, Integer period) {
		checkSlot(date, period);
		return reservationRepository.findWithStudents(instructorId, date, period);
	}

	// 새로 적거나 고치는 것은 근무 칸(확정·조건부)에서만. 지우는 것은 근무가 없어진 칸에서도 가능
	// instructorId가 null이면 미배정 칸: 근무 검사 없음
	// 글을 비워도 고른 수강생이 있으면 예약은 남음 (글만 지움)
	// original = 화면을 열 때 보였던 글. 그 사이 다른 사람이 고쳤으면 덮어쓰지 않고 막음 (null이면 검사 안 함)
	public void save(Long instructorId, LocalDate date, Integer period, String content, String original) {
		checkSlot(date, period);
		String text = limit(blankToNull(content), 100, "메모");
		var existing = findSlot(instructorId, date, period);
		checkNotChanged(existing.map(Reservation::getContent).orElse(""), original, text);
		if (text == null) {
			existing.ifPresent(r -> {
				r.change("");
				deleteIfEmpty(r);
			});
			return;
		}
		Instructor instructor = instructorId == null ? null : workingInstructor(instructorId, date, period);
		existing.ifPresentOrElse(r -> r.change(text),
			() -> reservationRepository.save(new Reservation(instructor, date, period, text)));
	}

	// 예약(등록 수강생)을 바꿈 (비우면 수강생만 뺌). 강사 칸은 근무 칸에서만, 미배정 칸은 근무 검사 없음
	public void saveStudents(Long instructorId, LocalDate date, Integer period, Set<Long> studentIds) {
		checkSlot(date, period);
		List<Student> students = studentIds == null ? List.of() : studentRepository.findAllById(studentIds);
		if (studentIds != null && students.size() != studentIds.size()) {
			throw new IllegalArgumentException("수강생을 찾을 수 없습니다.");
		}
		var existing = findSlot(instructorId, date, period);
		if (students.isEmpty() && existing.isEmpty()) {
			return;
		}
		Instructor instructor = instructorId == null ? null : workingInstructor(instructorId, date, period);
		Reservation r = existing.orElseGet(() -> reservationRepository.save(new Reservation(instructor, date, period, "")));
		r.changeStudents(students);
		deleteIfEmpty(r);
	}

	// 칸의 예약을 통째로 지움 (글 + 수강생)
	public void delete(Long instructorId, LocalDate date, Integer period) {
		checkSlot(date, period);
		findSlot(instructorId, date, period).ifPresent(reservationRepository::delete);
	}

	// 수강생을 지우기 전에 그 수강생을 고른 예약에서 뺌 (reservation_student 외래 키)
	public void removeStudent(Student student) {
		for (Reservation r : reservationRepository.findByStudentsId(student.getId())) {
			r.removeStudent(student);
			deleteIfEmpty(r);
		}
	}

	// 미배정 예약을 그 교시에 근무하는 강사 칸으로 옮김. 그 칸에 예약이 이미 있으면 메모는 ", "로 이어 붙이고 수강생은 합침
	public void assign(LocalDate date, Integer period, Long instructorId) {
		checkSlot(date, period);
		if (instructorId == null) {
			throw new IllegalArgumentException("배정할 강사를 고르세요.");
		}
		Reservation unassigned = reservationRepository.findByInstructorIsNullAndReservationDateAndPeriod(date, period)
			.orElseThrow(() -> new IllegalArgumentException("미배정 예약을 찾을 수 없습니다."));
		Instructor instructor = workingInstructor(instructorId, date, period);
		var existing = reservationRepository.findByInstructorIdAndReservationDateAndPeriod(instructorId, date, period);
		if (existing.isPresent()) {
			Reservation target = existing.get();
			String before = target.getContent();
			String added = unassigned.getContent();
			target.change(limit(before.isEmpty() || added.isEmpty() ? before + added : before + ", " + added, 100, "메모"));
			target.addStudents(unassigned.getStudents());
			reservationRepository.delete(unassigned);
		} else {
			unassigned.assign(instructor);
		}
	}

	// 낙관적 잠금: 버전 컬럼 대신 "보였던 글"과 지금 글을 비교 (기존 행에 버전 값이 없어도 되도록)
	// 지금 글이 새로 적은 글과 같으면 결과가 같으므로 통과
	private static void checkNotChanged(String current, String original, String text) {
		if (original == null) {
			return;
		}
		String seen = original.strip();
		String wanted = text == null ? "" : text;
		if (current.equals(seen) || current.equals(wanted)) {
			return;
		}
		String changed = current.isEmpty() ? "다른 분이 먼저 메모를 지웠습니다."
			: "다른 분이 먼저 메모를 '" + current + "'(으)로 바꿨습니다.";
		throw new IllegalArgumentException(changed + (wanted.isEmpty() ? " 지우지 않았으니"
			: " 입력하신 '" + wanted + "'는 저장하지 않았으니") + " 확인 후 다시 해 주세요.");
	}

	private void deleteIfEmpty(Reservation r) {
		if (r.isEmpty()) {
			reservationRepository.delete(r);
		}
	}

	private static void checkSlot(LocalDate date, Integer period) {
		if (date == null || period == null || !PeriodUtil.isValid(period)) {
			throw new IllegalArgumentException("날짜·교시를 확인하세요.");
		}
	}

	private Optional<Reservation> findSlot(Long instructorId, LocalDate date, int period) {
		return instructorId == null
			? reservationRepository.findByInstructorIsNullAndReservationDateAndPeriod(date, period)
			: reservationRepository.findByInstructorIdAndReservationDateAndPeriod(instructorId, date, period);
	}

	private Instructor workingInstructor(Long instructorId, LocalDate date, int period) {
		Instructor instructor = instructorRepository.findById(instructorId)
			.orElseThrow(() -> new IllegalArgumentException("강사를 찾을 수 없습니다: " + instructorId));
		if (!working(instructorId, date, period)) {
			throw new IllegalArgumentException(instructor.getName() + " 강사님은 " + period + "교시에 근무하지 않아 예약할 수 없습니다.");
		}
		return instructor;
	}

	private boolean working(Long instructorId, LocalDate date, int period) {
		PeriodSchedule p = scheduleService.getSchedule(date).period(period);
		return p.confirmed().stream().map(Entry::instructorId).anyMatch(instructorId::equals)
			|| p.conditional().stream().map(Entry::instructorId).anyMatch(instructorId::equals);
	}
}
