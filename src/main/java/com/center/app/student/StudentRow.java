package com.center.app.student;

import java.util.List;

// 수강생 목록 한 줄 (방문 회차는 저장하지 않고 수업 기록 수로 계산)
public record StudentRow(
	Long id,
	String name,
	long visitCount,
	List<String> hopeInstructorNames,  // 이름순
	List<Long> hopeInstructorIds       // 수업 기록 폼의 희망 강사 알림용
) {
}
