package com.center.app.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.center.app.consultation.Consultation;
import com.center.app.consultation.ConsultationRepository;
import com.center.app.consultation.ConsultationService;
import org.apache.poi.ss.usermodel.CellStyle;
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
class ConsultationImportTest {

	@Autowired
	ConsultationService service;

	@Autowired
	ConsultationRepository repository;

	@Autowired
	Clock clock;

	@TempDir
	Path dir;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	// 실제 업무 시트와 같은 모양: 1행 제목, 3행 머리글(B열부터), 시간·날짜는 엑셀 값, "-"는 빈 칸, 아래쪽은 번호 수식만 있는 빈 줄
	static byte[] sheet() throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			wb.createSheet("수강생");
			Sheet s = wb.createSheet("상담");
			CellStyle time = wb.createCellStyle();
			time.setDataFormat(wb.createDataFormat().getFormat("HH:mm"));
			CellStyle date = wb.createCellStyle();
			date.setDataFormat(wb.createDataFormat().getFormat("mm\"/\"dd"));

			s.createRow(0).createCell(1).setCellValue("연락 및 상담 현황");
			Row h = s.createRow(2);
			String[] heads = {"인원", "성명", "연락처", "희망 수업", "희망 요일", "희망 시간", "비고", "상담일"};
			for (int i = 0; i < heads.length; i++) {
				h.createCell(i + 1).setCellValue(heads[i]);
			}

			Row r1 = s.createRow(3);
			r1.createCell(1).setCellValue(1);
			r1.createCell(2).setCellValue("이길용");
			r1.createCell(3).setCellValue("010-3322-6889");
			r1.createCell(4).setCellValue("스마트폰 기초");
			r1.createCell(5).setCellValue("매일");
			r1.createCell(6).setCellValue(9.5 / 24);
			r1.getCell(6).setCellStyle(time);
			r1.createCell(7).setCellValue("(전화) 월요일 9:30 방문 예정");
			r1.createCell(8).setCellValue(LocalDateTime.of(2026, 10, 24, 0, 0));
			r1.getCell(8).setCellStyle(date);

			// 이름 없이 연락처만, 희망 요일·시간은 "-"
			Row r2 = s.createRow(4);
			r2.createCell(1).setCellValue(2);
			r2.createCell(3).setCellValue("010-2959-3032");
			r2.createCell(5).setCellValue("-");
			r2.createCell(6).setCellValue("-");
			r2.createCell(7).setCellValue("(전화) 부재중");

			// 상담일을 글로 적은 줄
			Row r3 = s.createRow(5);
			r3.createCell(2).setCellValue("이추경");
			r3.createCell(8).setCellValue("10/30");

			// 번호 칸만 수식으로 채워진 빈 줄
			s.createRow(6).createCell(1).setCellFormula("IF(C7=\"\",\"\",ROW()-3)");

			wb.write(out);
			return out.toByteArray();
		}
	}

	@Test
	void 상담_시트를_머리글_기준으로_읽는다() throws IOException {
		List<Consultation.Info> rows = new ConsultationSheetParser().parse(new ByteArrayInputStream(sheet()), 2026);

		assertThat(rows).containsExactly(
			new Consultation.Info("이길용", "010-3322-6889", "스마트폰 기초", "매일", "09:30",
				"(전화) 월요일 9:30 방문 예정", LocalDate.of(2026, 10, 24)),
			new Consultation.Info(null, "010-2959-3032", null, null, null, "(전화) 부재중", null),
			new Consultation.Info("이추경", null, null, null, null, null, LocalDate.of(2026, 10, 30)));
	}

	@Test
	void 상담_시트가_없으면_알린다() throws IOException {
		byte[] bytes;
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			wb.createSheet("수강생");
			wb.write(out);
			bytes = out.toByteArray();
		}
		assertThatThrownBy(() -> new ConsultationSheetParser().parse(new ByteArrayInputStream(bytes), 2026))
			.hasMessage("엑셀에 \"상담\" 시트가 없습니다.");
	}

	@Test
	void 상담일지가_비어_있을_때_처음_한_번만_넣는다() throws IOException {
		Path file = dir.resolve("consultation.xlsx");
		Files.write(file, sheet());
		ConsultationImportRunner runner = new ConsultationImportRunner(service, clock);
		ReflectionTestUtils.setField(runner, "file", file.toString());

		runner.run();
		assertThat(repository.count()).isEqualTo(3);

		// 서버를 다시 켜도 이미 들어 있으니 또 넣지 않음
		runner.run();
		assertThat(repository.count()).isEqualTo(3);
	}

	@Test
	void 파일이_없으면_건너뛰고_서버는_계속_켜진다() {
		ConsultationImportRunner runner = new ConsultationImportRunner(service, clock);
		ReflectionTestUtils.setField(runner, "file", dir.resolve("없는파일.xlsx").toString());

		runner.run();
		assertThat(repository.count()).isZero();
	}
}
