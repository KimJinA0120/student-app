package com.center.app.instructor;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstructorRepository extends JpaRepository<Instructor, Long> {

	// 기본 배치까지 한 번의 쿼리로 가져옴 (강사마다 slots를 따로 조회하는 N+1 방지)
	@Query("select distinct i from Instructor i left join fetch i.slots order by i.name")
	List<Instructor> findAllWithSlots();
}
