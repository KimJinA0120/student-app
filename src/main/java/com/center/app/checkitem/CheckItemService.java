package com.center.app.checkitem;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.center.app.common.Inputs.optional;
import static com.center.app.common.Inputs.required;

// 확인 필요 목록: 등록 / 해결 체크(목록에서 빠짐) / 잘못 쓴 항목 삭제
// 내용 수정·해결 항목 되돌리기는 두지 않음 (요구사항: 해결 체크 후 새로 작성)
@Service
@RequiredArgsConstructor
@Transactional
public class CheckItemService {

	private final CheckItemRepository repository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<CheckItem> unresolved() {
		return repository.findByResolvedFalseOrderByIdAsc();
	}

	public void add(String content, String personName) {
		repository.save(new CheckItem(required(content, 500, "내용"), optional(personName, 30, "이름")));
	}

	public void resolve(Long id) {
		find(id).resolve(LocalDateTime.now(clock));
	}

	public void delete(Long id) {
		repository.delete(find(id));
	}

	private CheckItem find(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("확인 필요 항목을 찾을 수 없습니다: " + id));
	}
}
