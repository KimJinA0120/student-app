package com.center.app.migration;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.center.app.consultation.Consultation;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

// 업무 시트 엑셀의 "상담" 시트(연락 및 상담 현황)를 상담일지 입력값으로 바꿈
// 열 위치가 바뀌어도 되도록 "성명"·"연락처"가 있는 줄을 머리글로 찾아 머리글 이름으로 열을 정함
// 희망 시간은 엑셀 시간 값이라 화면에 보이는 그대로("09:30") 글로 읽고, "-"는 빈 칸으로 봄
public class ConsultationSheetParser {

	static final String SHEET = "상담";

	private final DataFormatter formatter = new DataFormatter();

	public ConsultationSheetParser() {
		// 수식 칸은 수식 글("IF(...)")이 아니라 엑셀이 저장해 둔 결과 값으로 읽음
		formatter.setUseCachedValuesForFormulaCells(true);
	}

	// defaultYear: 상담일이 "10/24"처럼 연도 없는 글로 적혀 있을 때 붙일 연도
	public List<Consultation.Info> parse(InputStream in, int defaultYear) throws IOException {
		try (Workbook workbook = WorkbookFactory.create(in)) {
			Sheet sheet = workbook.getSheet(SHEET);
			if (sheet == null) {
				throw new IllegalArgumentException("엑셀에 \"" + SHEET + "\" 시트가 없습니다.");
			}
			Row header = findHeader(sheet);
			Map<String, Integer> cols = columns(header);
			List<Consultation.Info> result = new ArrayList<>();
			for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
				Row row = sheet.getRow(r);
				if (row == null) {
					continue;
				}
				Consultation.Info info = new Consultation.Info(
					text(row, cols.get("성명")),
					text(row, cols.get("연락처")),
					text(row, cols.get("희망 수업")),
					text(row, cols.get("희망 요일")),
					text(row, cols.get("희망 시간")),
					text(row, cols.get("비고")),
					date(row, cols.get("상담일"), defaultYear));
				// 번호(인원) 칸만 수식으로 채워진 빈 줄은 건너뜀. 이름·연락처가 모두 없으면 누구인지 알 수 없어 뺌
				if (info.name() != null || info.phone() != null) {
					result.add(info);
				}
			}
			return result;
		}
	}

	private Row findHeader(Sheet sheet) {
		for (Row row : sheet) {
			Map<String, Integer> cols = columns(row);
			if (cols.containsKey("성명") && cols.containsKey("연락처")) {
				return row;
			}
		}
		throw new IllegalArgumentException("\"" + SHEET + "\" 시트에서 성명·연락처 머리글을 찾을 수 없습니다.");
	}

	// 머리글 이름 → 열 번호. "희망\n요일"처럼 줄바꿈·공백이 섞여 있어도 "희망 요일"로 맞춤
	private Map<String, Integer> columns(Row row) {
		Map<String, Integer> cols = new HashMap<>();
		for (Cell cell : row) {
			String name = formatter.formatCellValue(cell).replaceAll("\\s+", " ").trim();
			cols.putIfAbsent(name, cell.getColumnIndex());
		}
		return cols;
	}

	private String text(Row row, Integer col) {
		if (col == null) {
			return null;
		}
		Cell cell = row.getCell(col);
		if (cell == null) {
			return null;
		}
		String value = formatter.formatCellValue(cell).trim();
		return value.isEmpty() || value.equals("-") ? null : value;
	}

	private LocalDate date(Row row, Integer col, int defaultYear) {
		if (col == null) {
			return null;
		}
		Cell cell = row.getCell(col);
		if (cell == null) {
			return null;
		}
		if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
			return cell.getLocalDateTimeCellValue().toLocalDate();
		}
		String value = text(row, col);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(defaultYear + "/" + value, DateTimeFormatter.ofPattern("yyyy/M/d"));
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException((row.getRowNum() + 1) + "행 상담일을 읽을 수 없습니다: " + value);
		}
	}
}
