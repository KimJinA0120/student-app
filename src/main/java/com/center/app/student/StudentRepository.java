package com.center.app.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, Long> {

	// 희망 강사까지 한 번에 (open-in-view=false라 화면에서 지연 로딩 불가)
	@Query("select distinct s from Student s left join fetch s.hopeInstructors order by s.name, s.id")
	List<Student> findAllWithHopes();

	@Query("select s from Student s left join fetch s.hopeInstructors where s.id = :id")
	Optional<Student> findWithHopesById(Long id);

	@Query("select distinct s from Student s left join fetch s.hopeInstructors where s.id in :ids")
	List<Student> findAllWithHopesByIdIn(Iterable<Long> ids);
}
