package com.center.app.manager;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ManagerRepository extends JpaRepository<Manager, Long> {

	// 기본 근무표까지 한 번에 (N+1 방지, open-in-view=false라 화면에서 지연 로딩 불가)
	@Query("select distinct m from Manager m left join fetch m.shifts order by m.name")
	List<Manager> findAllWithShifts();

	@Query("select m from Manager m left join fetch m.shifts where m.id = :id")
	Optional<Manager> findWithShiftsById(Long id);
}
