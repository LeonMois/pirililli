package pirililli;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal .xlsx reading/writing: first sheet, first row is the header. */
final class Spreadsheets {
    private Spreadsheets() {}

    private static final Pattern LANG_IN_PARENS = Pattern.compile(".*\\(([A-Za-z]{2,3})\\).*");

    record Table(List<String> header, List<List<String>> rows) {}

    /** Reads the header and all non-empty data rows as trimmed strings. */
    static Table read(Path file) throws IOException {
        List<String> header = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String v = cell == null ? "" : fmt.formatCellValue(cell, eval).trim();
                    any |= !v.isEmpty();
                    cells.add(v);
                }
                if (row.getRowNum() == 0) header = cells;
                else if (any) rows.add(cells);
            }
        }
        return new Table(header, rows);
    }

    static void write(Path file, String sheetName, List<String> header, List<List<String>> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            Row h = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(header.get(i));
                c.setCellStyle(bold);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> vals = rows.get(r);
                for (int i = 0; i < vals.size(); i++) {
                    Cell c = row.createCell(i);
                    c.setCellStyle(text); // keep leading zeros / numbers as typed
                    c.setCellValue(vals.get(i));
                }
            }
            for (int i = 0; i < header.size(); i++) {
                sheet.setDefaultColumnStyle(i, text);
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i) + 512, 12 * 256), 80 * 256));
            }
            sheet.createFreezePane(0, 1);
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
    }

    static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i).trim() : "";
    }

    /** Language code of a header like "pt", "EN" or "Question (PT)"; lower case. */
    static String languageCode(String header) {
        Matcher m = LANG_IN_PARENS.matcher(header);
        return (m.matches() ? m.group(1) : header).trim().toLowerCase(Locale.ROOT);
    }
}
