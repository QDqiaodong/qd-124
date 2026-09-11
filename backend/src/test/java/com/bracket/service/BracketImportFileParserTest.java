package com.bracket.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BracketImportFileParserTest {

    private final BracketImportFileParser parser = new BracketImportFileParser();

    private MultipartFile file(String name, byte[] bytes, String contentType) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    @Test
    void parsesCsvWithUtf8BomAndQuotedCommas() {
        String content = "﻿支架名称,支架型号,长(mm),宽(mm)\n"
                + "\"支架,一号\",ST-A,300,150\r\n"
                + "\"带\"\"引号\"\"支架\",ST-B,310,160\n";
        MultipartFile csv = file("a.csv", content.getBytes(StandardCharsets.UTF_8), "text/csv");

        List<BracketImportFileParser.RawRow> rows = parser.parse(csv);

        // BOM 不出现在表头单元格中
        assertEquals("支架名称", rows.get(0).getCells().get(0));
        assertEquals(4, rows.get(0).getCells().size());
        // 引号内逗号不拆分
        assertEquals("支架,一号", rows.get(1).getCells().get(0));
        // 转义双引号还原；CRLF 的 \r 不进入单元格
        assertEquals("带\"引号\"支架", rows.get(2).getCells().get(0));
        assertEquals("160", rows.get(2).getCells().get(3));
        // 物理行号连续
        assertEquals(3, rows.get(2).getRowNum());
    }

    @Test
    void fallsBackToGbk_whenUtf8DecodeFails() {
        byte[] bytes = ("支架名称,支架型号,长(mm),宽(mm)\n"
                + "A型支架,ST-A,300,150\n").getBytes(Charset.forName("GBK"));
        MultipartFile csv = file("gbk.csv", bytes, "text/csv");

        List<BracketImportFileParser.RawRow> rows = parser.parse(csv);

        assertEquals("支架名称", rows.get(0).getCells().get(0));
        assertEquals("A型支架", rows.get(1).getCells().get(0));
    }

    @Test
    void parsesRealXlsxWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("支架");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("支架名称");
            header.createCell(1).setCellValue("支架型号");
            header.createCell(2).setCellValue("长(mm)");
            header.createCell(3).setCellValue("宽(mm)");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("A型支架");
            data.createCell(1).setCellValue("ST-A");
            data.createCell(2).setCellValue(300);
            data.createCell(3).setCellValue(150.5);
            workbook.write(out);
        }
        MultipartFile xlsx = file("a.xlsx", out.toByteArray(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        List<BracketImportFileParser.RawRow> rows = parser.parse(xlsx);

        assertEquals("支架名称", rows.get(0).getCells().get(0));
        assertEquals("A型支架", rows.get(1).getCells().get(0));
        assertEquals("300", rows.get(1).getCells().get(2));
        assertEquals("150.5", rows.get(1).getCells().get(3));
        assertEquals(2, rows.get(1).getRowNum());
    }

    @Test
    void rejectsUnsupportedExtension() {
        MultipartFile txt = file("a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(txt));
        assertTrue(ex.getMessage().contains("不支持的文件格式"));
    }

    @Test
    void rejectsEmptyFile() {
        MultipartFile empty = file("a.csv", new byte[0], "text/csv");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(empty));
    }
}
