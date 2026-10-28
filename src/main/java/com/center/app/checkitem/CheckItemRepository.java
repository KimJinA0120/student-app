package com.center.app.checkitem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckItemRepository extends JpaRepository<CheckItem, Long> {

	// 남은 항목 (먼저 적은 순)
	List<CheckItem> findByResolvedFalseOrderByIdAsc();
}
