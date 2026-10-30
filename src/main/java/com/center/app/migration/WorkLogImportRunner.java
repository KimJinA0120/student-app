package com.center.app.migration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.center.app.worklog.WorkLog;
import com.center.app.worklog.WorkLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// 업무일지 첫 이전 실행기: 서버를 켤 때마다 확인하지만, 업무일지가 비어 있을 때만 엑셀을 읽어 넣음 → 처음 한 번만 들어감
// 상담일지와 같은 엑셀 파일(app.sheet-import.file)의 "업무일지" 시트를 읽음
// 파일이 없거나 읽다가 오류가 나도 서버는 그대로 켜짐 (로그만 남김, 한 건도 넣지 않음)
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkLogImportRunner implements CommandLineRunner {

	private final WorkLogService workLogService;

	@Value("${app.sheet-import.file:}")
	private String file;

	@Override
	public void run(String... args) {
		if (file.isBlank() || !workLogService.isEmpty()) {
			return;
		}
		Path path = Path.of(file);
		if (!Files.exists(path)) {
			log.info("업무일지 이전 파일이 없어 건너뜁니다: {}", path.toAbsolutePath());
			return;
		}
		try (InputStream in = Files.newInputStream(path)) {
			List<WorkLog.Info> rows = new WorkLogSheetParser().parse(in);
			int count = workLogService.importAll(rows);
			log.info("업무일지 이전 완료: {}건 ({})", count, path.getFileName());
		} catch (Exception e) {
			log.error("업무일지 이전 실패 (아무것도 넣지 않았습니다): {}", e.getMessage(), e);
		}
	}
}
