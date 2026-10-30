package com.center.app.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.center.app.worklog.WorkLog;
import com.center.app.worklog.WorkLogRepository;
import com.center.app.worklog.WorkLogService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorkLogImportTest {

	@Autowired
	WorkLogService service;

	@Autowired
	WorkLogRepository repository;

	@TempDir
	Path dir;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	// 실제 업무 시트와 같은 모양: 1행 제목, 3행 누적 합계, 5·6행 두 줄 머리글, 7행부터 날짜(엑셀 날짜 값)
	// 요일·계는 수식, 앞으로 쓸 날은 날짜만 있는 빈 줄
	static byte[] sheet(String period1Value) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			wb.createSheet("상담");
			Sheet s = wb.createSheet("업무일지");
			s.createRow(0).createCell(0).setCellValue("안양 상설교육장 업무일지");
			Row totals = s.createRow(2);
			totals.createCell(0).setCellValue("누적 강좌");
			totals.createCell(1).setCellFormula("SUM(G7:G74)");

			Row h = s.createRow(4);
			h.createCell(0).setCellValue("날짜");
			h.createCell(1).setCellValue("요일");
			h.createCell(2).setCellValue("금일 수업 강좌수");
			h.createCell(7).setCellValue("강사");
			h.createCell(8).setCellValue("수강생(명)");
			h.createCell(11).setCellValue("특이사항");
			Row sub = s.createRow(5);
			String[] subs = {"1교시", "2교시", "3교시", "4교시", "계", null, "신규", "기존", "계", "  "};
			for (int i = 0; i < subs.length; i++) {
				if (subs[i] != null) {
					sub.createCell(i + 2).setCellValue(subs[i]);
				}
			}

			Row r1 = s.createRow(6);
			r1.createCell(0).setCellValue(LocalDateTime.of(2026, 10, 25, 0, 0));
			r1.createCell(1).setCellFormula("TEXT(A7,\"ddd\")");
			r1.createCell(4).setCellValue(1);
			r1.createCell(6).setCellFormula("IF(COUNT(C7:F7)=0,\"\",SUM(C7:F7))");
			r1.createCell(8).setCellValue(1);

			Row r2 = s.createRow(7);
			r2.createCell(0).setCellValue(LocalDateTime.of(2026, 10, 27, 0, 0));
			r2.createCell(2).setCellValue(period1Value);
			r2.createCell(3).setCellValue(4);
			r2.createCell(7).setCellValue(" \n - 1교시 강사 출석 완료\n - 2교시 강사 출석 완료");
			r2.createCell(8).setCellValue(6);
			r2.createCell(9).setCellValue(13);
			r2.createCell(11).setCellValue("\n - 205호 wifi 안됨\n    (총 6 수업)\n");

			// 날짜만 있는 줄
			Row r3 = s.createRow(8);
			r3.createCell(0).setCellValue(LocalDateTime.of(2026, 10, 28, 0, 0));
			r3.createCell(6).setCellFormula("IF(COUNT(C9:F9)=0,\"\",SUM(C9:F9))");

			wb.write(out);
			return out.toByteArray();
		}
	}

	@Test
	void 업무일지_시트를_두_줄_머리글_기준으로_읽는다() throws IOException {
		List<WorkLog.Info> rows = new WorkLogSheetParser().parse(new ByteArrayInputStream(sheet("3")));

		assertThat(rows).containsExactly(
			new WorkLog.Info(LocalDate.of(2026, 10, 25), null, null, 1, null, 1, null, null, null),
			new WorkLog.Info(LocalDate.of(2026, 10, 27), 3, 4, null, null, 6, 13,
				"- 1교시 강사 출석 완료\n- 2교시 강사 출석 완료", "- 205호 wifi 안됨\n  (총 6 수업)"));
	}

	@Test
	void 숫자_칸에_글이_있으면_몇_행인지_알린다() {
		assertThatThrownBy(() -> new WorkLogSheetParser().parse(new ByteArrayInputStream(sheet("세 개"))))
			.hasMessage("8행 숫자를 읽을 수 없습니다: 세 개");
	}

	@Test
	void 업무일지가_비어_있을_때_처음_한_번만_넣는다() throws IOException {
		Path file = dir.resolve("sheet.xlsx");
		Files.write(file, sheet("3"));
		WorkLogImportRunner runner = new WorkLogImportRunner(service);
		ReflectionTestUtils.setField(runner, "file", file.toString());

		runner.run();
		assertThat(repository.count()).isEqualTo(2);

		runner.run();
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void 파일이_없으면_건너뛰고_서버는_계속_켜진다() {
		WorkLogImportRunner runner = new WorkLogImportRunner(service);
		ReflectionTestUtils.setField(runner, "file", dir.resolve("없는파일.xlsx").toString());

		runner.run();
		assertThat(repository.count()).isZero();
	}
}
