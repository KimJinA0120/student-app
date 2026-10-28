package com.center.app.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PeriodSetConverterTest {

	private final PeriodSetConverter converter = new PeriodSetConverter();

	@Test
	void 집합을_정렬된_문자열로_저장한다() {
		assertThat(converter.convertToDatabaseColumn(Set.of(3, 1))).isEqualTo("1,3");
	}

	@Test
	void 빈_집합은_NULL_전_교시() {
		assertThat(converter.convertToDatabaseColumn(Set.of())).isNull();
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
	}

	@Test
	void 문자열을_집합으로_읽는다() {
		assertThat(converter.convertToEntityAttribute("1,3")).containsExactly(1, 3);
		assertThat(converter.convertToEntityAttribute(null)).isEmpty();
	}
}
