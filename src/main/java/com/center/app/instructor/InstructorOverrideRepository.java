package com.center.app.instructor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstructorOverrideRepository extends JpaRepository<InstructorOverride, Long> {

	// [from, to] 기간과 하루라도 겹치는 예외
	@Query("select o from InstructorOverride o where o.startDate <= :to and o.endDate >= :from")
	List<InstructorOverride> findOverlapping(LocalDate from, LocalDate to);
}
