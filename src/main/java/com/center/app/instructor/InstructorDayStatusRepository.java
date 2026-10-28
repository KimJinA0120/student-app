package com.center.app.instructor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructorDayStatusRepository extends JpaRepository<InstructorDayStatus, Long> {

	List<InstructorDayStatus> findByStatusDateBetween(LocalDate from, LocalDate to);
}
