package com.center.app.manager;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerExceptionRepository extends JpaRepository<ManagerException, Long> {

	List<ManagerException> findByExceptionDate(LocalDate exceptionDate);

	List<ManagerException> findByManagerIdOrderByExceptionDate(Long managerId);

	boolean existsByManagerIdAndExceptionDate(Long managerId, LocalDate exceptionDate);
}
