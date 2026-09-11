package com.bracket.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入文件解析器：支持 .csv（UTF-8/GBK，带 BOM）与 .xls/.xlsx（第一个工作表）。
 * 仅负责把文件解析成"行号 + 单元格字符串"，表头识别与业务校验在 {@link BracketImportService} 中完成。
 */
@Component
public class BracketImportFileParser {

    /** 原始解析行：rowNum 为文件中的物理行号（表头为 1），cells 已 trim。 */
    public static class RawRow {
        private final int rowNum;
        private final List<String> cells;

        public RawRow(int rowNum, List<String> cells) {
            this.rowNum = rowNum;
            this.cells = cells;
        }

        public int getRowNum() {
            return rowNum;
        }

        public List<String> getCells() {
            return cells;
        }
    }

    public List<RawRow> parse(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (filename.endsWith(".csv")) {
            return parseCsv(safeBytes(file));
        }
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return parseExcel(safeBytes(file));
        }
        throw new IllegalArgumentException("不支持的文件格式，请上传 .csv、.xlsx 或 .xls 文件");
    }

    private byte[] safeBytes(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("导入文件为空");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取导入文件失败：" + e.getMessage());
        }
    }

    private List<RawRow> parseExcel(byte[] bytes) {
        List<RawRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 中没有可读取的工作表");
            }
            int lastRow = sheet.getLastRowNum();
            for (int i = 0; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int lastCell = row.getLastCellNum();
                    for (int j = 0; j < lastCell; j++) {
                        Cell cell = row.getCell(j);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                }
                rows.add(new RawRow(i + 1, cells));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 文件解析失败，请确认文件未损坏且为标准表格：" + e.getMessage());
        }
        return rows;
    }

    private List<RawRow> parseCsv(byte[] bytes) {
        String content = decodeCsv(bytes);
        // 去掉解码后残留在内容开头的 BOM
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        List<RawRow> rows = new ArrayList<>();
        List<String> field = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;
        int physicalLine = 1;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                    if (c == '\n') {
                        physicalLine++;
                    }
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    rowHasContent = true;
                }
                case ',' -> {
                    field.add(current.toString().trim());
                    current.setLength(0);
                }
                case '\r' -> {
                    // 与 \n 配合处理 CRLF，忽略单独的 \r
                }
                case '\n' -> {
                    field.add(current.toString().trim());
                    current.setLength(0);
                    rows.add(new RawRow(physicalLine, new ArrayList<>(field)));
                    field.clear();
                    rowHasContent = false;
                    physicalLine++;
                }
                default -> {
                    current.append(c);
                    if (!Character.isWhitespace(c)) {
                        rowHasContent = true;
                    }
                }
            }
        }
        // 最后一行没有换行符
        if (current.length() > 0 || !field.isEmpty() || rowHasContent) {
            field.add(current.toString().trim());
            rows.add(new RawRow(physicalLine, new ArrayList<>(field)));
        }
        return rows;
    }

    /**
     * CSV 无 BOM 时优先按 UTF-8 解码；Excel 导出的 GBK 文件解码失败则回退 GBK。
     */
    private String decodeCsv(byte[] bytes) {
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
            return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer)
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }
}
