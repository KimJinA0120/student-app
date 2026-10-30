package com.center.app.lesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// 시간표에서 강사 이름까지 쓰므로 함께 읽음 (open-in-view=false라 화면에서 지연 로딩 불가)
	// 미배정 예약(강사 null)도 함께 읽도록 left join. 고른 수강생도 함께 (distinct = 수강생 수만큼 줄이 늘지 않게)
	@Query("select distinct r from Reservation r left join fetch r.instructor left join fetch r.students"
		+ " where r.reservationDate = :date")
	List<Reservation> findWithInstructorByDate(LocalDate date);

	// 예약 수강생 창용 칸 하나. instructorId가 null이면 미배정 칸
	@Query("select r from Reservation r left join r.instructor i left join fetch r.students"
		+ " where r.reservationDate = :date and r.period = :period"
		+ " and ((:instructorId is null and i is null) or i.id = :instructorId)")
	Optional<Reservation> findWithStudents(Long instructorId, LocalDate date, int period);

	// 수강생을 지울 때 그 수강생을 고른 예약에서 빼기 위함
	List<Reservation> findByStudentsId(Long studentId);

	Optional<Reservation> findByInstructorIdAndReservationDateAndPeriod(Long instructorId, LocalDate date, int period);

	// 미배정 칸 (날짜·교시당 1행은 ReservationController의 synchronized로 지킴. UNIQUE 제약은 NULL끼리 중복으로 보지 않음)
	Optional<Reservation> findByInstructorIsNullAndReservationDateAndPeriod(LocalDate date, int period);
}
