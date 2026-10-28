package com.center.app.migration;

import java.nio.file.Files;
import java.nio.file.Path;

import com.center.app.instructor.InstructorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// 기존 강사 일정 이전 실행기. 설정이 있고 강사 테이블이 비어 있을 때만 한 번 실행
// 사용: java -jar app.jar --app.legacy-import.file=강사일정.html
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyImportRunner implements CommandLineRunner {

	private final InstructorRepository instructorRepository;
	private final LegacyScheduleImporter importer;

	@Value("${app.legacy-import.file:}")
	private String file;

	@Override
	public void run(String... args) throws Exception {
		if (file.isBlank()) {
			return;
		}
		if (instructorRepository.count() > 0) {
			log.info("강사 데이터가 이미 있어 기존 일정 이전을 건너뜁니다.");
			return;
		}
		LegacySchedule legacy = new LegacyScheduleParser().parse(Files.readString(Path.of(file)));
		LegacyScheduleImporter.Result result = importer.importFrom(legacy);
		log.info("기존 일정 이전 완료: 강사 {}명, 기본 배치 {}건, 날짜별 예외 {}건, 확인 필요 {}건",
			result.instructors(), result.slots(), result.overrides(), result.checkItems());
	}
}
