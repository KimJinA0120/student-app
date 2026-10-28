package com.center.app.instructor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InstructorRepository extends JpaRepository<Instructor, Long> {

	// 기본 배치까지 한 번의 쿼리로 가져옴 (강사마다 slots를 따로 조회하는 N+1 방지)
	@Query("select distinct i from Instructor i left join fetch i.slots order by i.name")
	List<Instructor> findAllWithSlots();

	@Query("select i from Instructor i left join fetch i.slots where i.id = :id")
	Optional<Instructor> findWithSlotsById(Long id);

	List<Instructor> findAllByOrderByName();

	List<Instructor> findByNameContainingOrderByName(String keyword);

	// 수강생의 희망 강사 연결 행 삭제 (Student 쪽 컬렉션이라 강사를 지울 때 직접 정리해야 FK 오류가 안 남)
	// SQL로 직접 지우므로, 메모리에 올라와 있던 엔티티가 지운 행을 다시 쓰지 않도록 전후로 flush·clear
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "delete from student_hope_instructor where instructor_id = :instructorId", nativeQuery = true)
	void deleteHopeLinks(Long instructorId);
}
