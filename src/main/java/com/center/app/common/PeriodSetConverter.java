package com.center.app.common;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// 교시 집합 Set<Integer> ↔ DB 문자열 "1,3" 변환. NULL/빈 Set = 전 교시
// 사용하는 필드에 @Convert(converter = PeriodSetConverter.class)로 지정
@Converter
public class PeriodSetConverter implements AttributeConverter<Set<Integer>, String> {

	@Override
	public String convertToDatabaseColumn(Set<Integer> periods) {
		if (periods == null || periods.isEmpty()) {
			return null;
		}
		return new TreeSet<>(periods).stream()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
	}

	@Override
	public Set<Integer> convertToEntityAttribute(String column) {
		if (column == null || column.isBlank()) {
			return new TreeSet<>();
		}
		return Arrays.stream(column.split(","))
			.map(String::trim)
			.map(Integer::valueOf)
			.collect(Collectors.toCollection(TreeSet::new));
	}
}
