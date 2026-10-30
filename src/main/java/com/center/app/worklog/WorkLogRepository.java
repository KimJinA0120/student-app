package com.center.app.worklog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

	List<WorkLog> findAllByOrderByLogDateDesc();

	Optional<WorkLog> findByLogDate(LocalDate logDate);
}
