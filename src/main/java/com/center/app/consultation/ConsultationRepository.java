package com.center.app.consultation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

	// 목록에 등록한 수강생 이름까지 쓰므로 함께 읽음 (open-in-view=false라 화면에서 지연 로딩 불가)
	// 최근 상담일순, 상담일이 없으면 맨 뒤
	@Query("select c from Consultation c left join fetch c.student"
		+ " order by c.consultDate desc nulls last, c.id desc")
	List<Consultation> findAllWithStudent();

	@Query("select c from Consultation c left join fetch c.student where c.id = :id")
	Optional<Consultation> findWithStudentById(Long id);

	// 수강생을 지울 때 연결을 끊기 위함
	List<Consultation> findByStudentId(Long studentId);
}
