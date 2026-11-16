package pirililli;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The configuration tables: CSV files (UTF-8, first row is the header) that Numbers, Excel, LibreOffice or a text
 * editor can open. Comma, semicolon or tab separated (detected from the header line).
 */
final class Spreadsheets {
    private Spreadsheets() {}

    private static final Pattern LANG_IN_PARENS = Pattern.compile(".*\\(([A-Za-z]{2,3})\\).*");
    private static final char BOM = '\uFEFF';

    record Table(List<String> header, List<List<String>> rows) {}

    /** Reads the header and all non-empty data rows as trimmed strings. */
    static Table read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == BOM) text = text.substring(1);
        List<List<String>> records = parse(text, delimiter(text));
        List<String> header = records.isEmpty() ? List.of() : records.get(0);
        List<List<String>> rows = new ArrayList<>();
        for (List<String> r : records.subList(Math.min(1, records.size()), records.size())) {
            if (r.stream().anyMatch(v -> !v.isEmpty())) rows.add(r);
        }
        return new Table(header, rows);
    }

    static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        StringBuilder sb = new StringBuilder().append(BOM); // BOM so Excel also detects UTF-8
        line(sb, header);
        for (List<String> row : rows) line(sb, row);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, sb, StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i) == null ? "" : values.get(i);
            if (v.matches("(?s).*[\",;\t\r\n].*") || !v.equals(v.trim())) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append("\r\n");
    }

    /** The most frequent of , ; and tab in the first line (outside quotes); Numbers uses ; in some languages. */
    private static char delimiter(String text) {
        int comma = 0, semi = 0, tab = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && (c == '\n' || c == '\r')) break;
            else if (!quoted && c == ',') comma++;
            else if (!quoted && c == ';') semi++;
            else if (!quoted && c == '\t') tab++;
        }
        if (semi > comma && semi >= tab) return ';';
        if (tab > comma && tab > semi) return '\t';
        return ',';
    }

    /** RFC 4180 style: quoted fields may contain the delimiter, newlines and doubled quotes. */
    private static List<List<String>> parse(String text, char delim) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"') {
                quoted = true; any = true;
            } else if (c == delim) {
                record.add(field.toString().trim()); field.setLength(0); any = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (any || field.length() > 0) record.add(field.toString().trim());
                records.add(record);
                record = new ArrayList<>(); field.setLength(0); any = false;
            } else {
                field.append(c); any = true;
            }
        }
        if (any || field.length() > 0) { record.add(field.toString().trim()); records.add(record); }
        return records;
    }

    /** Reads the first sheet of an old .xlsx file (only used to convert it to CSV once). */
    static Table readXlsx(Path file) throws IOException {
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

    static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i).trim() : "";
    }

    /** Language code of a header like "pt", "EN" or "Question (PT)"; lower case. */
    static String languageCode(String header) {
        Matcher m = LANG_IN_PARENS.matcher(header);
        return (m.matches() ? m.group(1) : header).trim().toLowerCase(Locale.ROOT);
    }
}
