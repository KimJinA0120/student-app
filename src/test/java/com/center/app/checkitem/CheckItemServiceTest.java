package com.center.app.checkitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.center.app.common.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(FixedClockConfig.class)
@Transactional
class CheckItemServiceTest {

	@Autowired
	CheckItemService service;

	@Autowired
	CheckItemRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	@Test
	void 등록하고_해결하면_목록에서_빠진다() {
		service.add(" 11/9 결근 확인 ", " ");
		service.add("연락처 확인", "김강사");

		assertThat(service.unresolved()).extracting(CheckItem::getContent).containsExactly("11/9 결근 확인", "연락처 확인");
		CheckItem first = service.unresolved().getFirst();
		assertThat(first.getPersonName()).isNull();

		service.resolve(first.getId());
		assertThat(service.unresolved()).extracting(CheckItem::getPersonName).containsExactly("김강사");
		CheckItem resolved = repository.findById(first.getId()).orElseThrow();
		assertThat(resolved.isResolved()).isTrue();
		assertThat(resolved.getResolvedAt()).isEqualTo(FixedClockConfig.NOW);
	}

	@Test
	void 해결된_항목은_최근_해결순으로_보이고_되돌리면_남은_목록으로_돌아간다() {
		service.add("첫 항목", null);
		service.add("둘째 항목", null);
		List<CheckItem> items = service.unresolved();
		service.resolve(items.get(0).getId());
		service.resolve(items.get(1).getId());

		// 해결 시각이 같으면 나중에 적은 항목이 먼저
		assertThat(service.resolved()).extracting(CheckItem::getContent).containsExactly("둘째 항목", "첫 항목");

		service.reopen(items.get(0).getId());
		assertThat(service.unresolved()).extracting(CheckItem::getContent).containsExactly("첫 항목");
		assertThat(service.unresolved().getFirst().getResolvedAt()).isNull();
		assertThat(service.resolved()).extracting(CheckItem::getContent).containsExactly("둘째 항목");
	}

	@Test
	void 잘못_쓴_항목은_삭제하고_빈_내용은_막는다() {
		service.add("잘못 쓴 내용", null);
		service.delete(service.unresolved().getFirst().getId());

		assertThat(repository.count()).isZero();
		assertThatThrownBy(() -> service.add(" ", "김강사")).hasMessage("내용을(를) 입력하세요.");
	}
}
