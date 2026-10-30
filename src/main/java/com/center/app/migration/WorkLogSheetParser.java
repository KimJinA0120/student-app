package com.center.app.migration;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.center.app.worklog.WorkLog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

// 업무 시트 엑셀의 "업무일지" 시트를 업무일지 입력값으로 바꿈
// 머리글이 두 줄: 윗줄(날짜·강사·특이사항) + 아랫줄(1교시~4교시·신규·기존). "날짜"·"특이사항"이 있는 줄을 윗줄로 찾음
// 날짜만 미리 적어 두고 내용이 없는 줄(앞으로 쓸 날)은 건너뜀. 요일·계는 엑셀 수식이라 읽지 않음(화면에서 계산)
public class WorkLogSheetParser {

	static final String SHEET = "업무일지";

	private final DataFormatter formatter = new DataFormatter();

	public List<WorkLog.Info> parse(InputStream in) throws IOException {
		try (Workbook workbook = WorkbookFactory.create(in)) {
			Sheet sheet = workbook.getSheet(SHEET);
			if (sheet == null) {
				throw new IllegalArgumentException("엑셀에 \"" + SHEET + "\" 시트가 없습니다.");
			}
			Row header = findHeader(sheet);
			Map<String, Integer> cols = columns(header);
			Row sub = sheet.getRow(header.getRowNum() + 1);
			if (sub != null) {
				// 윗줄 이름("강사" 등)이 아랫줄 빈 칸에 덮이지 않도록 아랫줄 이름만 더함
				columns(sub).forEach(cols::putIfAbsent);
			}
			for (String name : List.of("1교시", "2교시", "3교시", "4교시", "신규", "기존")) {
				if (!cols.containsKey(name)) {
					throw new IllegalArgumentException("\"" + SHEET + "\" 시트에서 \"" + name + "\" 머리글을 찾을 수 없습니다.");
				}
			}
			List<WorkLog.Info> result = new ArrayList<>();
			for (int r = header.getRowNum() + 2; r <= sheet.getLastRowNum(); r++) {
				Row row = sheet.getRow(r);
				LocalDate date = row == null ? null : date(row, cols.get("날짜"));
				if (date == null) {
					continue;
				}
				WorkLog.Info info = new WorkLog.Info(date,
					number(row, cols.get("1교시")),
					number(row, cols.get("2교시")),
					number(row, cols.get("3교시")),
					number(row, cols.get("4교시")),
					number(row, cols.get("신규")),
					number(row, cols.get("기존")),
					note(row, cols.get("강사")),
					note(row, cols.get("특이사항")));
				if (!isBlank(info)) {
					result.add(info);
				}
			}
			return result;
		}
	}

	private static boolean isBlank(WorkLog.Info i) {
		return i.period1() == null && i.period2() == null && i.period3() == null && i.period4() == null
			&& i.newCount() == null && i.existingCount() == null && i.instructorNote() == null && i.specialNote() == null;
	}

	private Row findHeader(Sheet sheet) {
		for (Row row : sheet) {
			Map<String, Integer> cols = columns(row);
			if (cols.containsKey("날짜") && cols.containsKey("특이사항")) {
				return row;
			}
		}
		throw new IllegalArgumentException("\"" + SHEET + "\" 시트에서 날짜·특이사항 머리글을 찾을 수 없습니다.");
	}

	// 머리글 이름 → 열 번호. 줄바꿈·공백이 섞여 있어도 한 칸 띄어쓰기로 맞춤
	private Map<String, Integer> columns(Row row) {
		Map<String, Integer> cols = new HashMap<>();
		for (Cell cell : row) {
			String name = formatter.formatCellValue(cell).replaceAll("\\s+", " ").trim();
			if (!name.isEmpty()) {
				cols.putIfAbsent(name, cell.getColumnIndex());
			}
		}
		return cols;
	}

	private String text(Row row, Integer col) {
		Cell cell = col == null ? null : row.getCell(col);
		if (cell == null) {
			return null;
		}
		String value = formatter.formatCellValue(cell).strip();
		return value.isEmpty() ? null : value;
	}

	// 엑셀 메모는 첫 줄만 붙어 있고 나머지는 " - "처럼 앞에 빈칸이 있어 카톡 글에서 줄이 어긋남
	// → "-"로 시작하는 줄은 앞 빈칸을 없애고, 이어지는 줄은 두 칸 들여쓰기로 맞춤
	private String note(Row row, Integer col) {
		String value = text(row, col);
		if (value == null) {
			return null;
		}
		return value.lines()
			.map(line -> line.isBlank() || line.strip().startsWith("-") || !Character.isWhitespace(line.charAt(0))
				? line.strip() : "  " + line.strip())
			.collect(Collectors.joining("\n"));
	}

	// 날짜 칸은 엑셀 날짜 값(일련번호)
	private LocalDate date(Row row, Integer col) {
		Cell cell = col == null ? null : row.getCell(col);
		if (cell == null || cell.getCellType() != CellType.NUMERIC) {
			return null;
		}
		return cell.getLocalDateTimeCellValue().toLocalDate();
	}

	// 숫자 칸. 빈 칸은 null, 소수나 글이면 몇 행인지 알려 줌
	private Integer number(Row row, Integer col) {
		Cell cell = row.getCell(col);
		if (cell == null || cell.getCellType() == CellType.BLANK) {
			return null;
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			double value = cell.getNumericCellValue();
			if (value == Math.rint(value)) {
				return (int) value;
			}
		} else {
			String value = text(row, col);
			if (value == null) {
				return null;
			}
			if (value.matches("\\d+")) {
				return Integer.parseInt(value);
			}
		}
		throw new IllegalArgumentException((row.getRowNum() + 1) + "행 숫자를 읽을 수 없습니다: "
			+ formatter.formatCellValue(cell));
	}
}
