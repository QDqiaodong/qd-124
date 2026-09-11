package com.bracket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量导入端到端测试：multipart 上传 → 解析 → 逐行校验 → 真实写 H2 库
 * → 成功/失败/跳过统计 → 模板下载。不依赖外部 MySQL/Redis（缓存失效失败被吞掉）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BracketImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    @Test
    void fullFlow_previewConfirmStats_andTemplateDownload() throws Exception {
        // 1) 模板下载：带 BOM、含标准表头
        MvcResult templateResult = mockMvc.perform(get("/api/bracket/import/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        String template = templateResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(template.startsWith("﻿"), "模板应以 UTF-8 BOM 开头");
        assertTrue(template.contains("支架名称,支架型号,长(mm),宽(mm)"));

        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "新型号A,ST-INT-A,300,150\n"
                + "新型号B,ST-INT-B,400,200\n"
                + "重名行,ST-INT-A,310,160\n"
                + "超长行,ST-INT-C,99999,150\n";

        // 2) 预览：2 通过、1 文件内重复、1 超范围
        MvcResult previewResult = mockMvc.perform(
                        multipart("/api/bracket/import/preview").file(csvFile("brackets.csv", csv)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode preview = dataOf(previewResult);
        assertEquals(4, preview.path("totalCount").asInt());
        assertEquals(2, preview.path("validCount").asInt());
        assertEquals(0, preview.path("duplicateCount").asInt());
        assertEquals(2, preview.path("invalidCount").asInt());

        // 3) 第一次确认：写入 2 行，2 行失败
        MvcResult confirmResult = mockMvc.perform(
                        multipart("/api/bracket/import/confirm").file(csvFile("brackets.csv", csv)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode result = dataOf(confirmResult);
        assertEquals(2, result.path("successCount").asInt());
        assertEquals(2, result.path("failedCount").asInt());
        assertEquals(0, result.path("skippedCount").asInt());
        assertEquals(2, result.path("failedRows").size());

        // 4) 再次上传同一文件：之前写入的型号现在"已存在"，应提示为跳过而非失败
        MvcResult secondResult = mockMvc.perform(
                        multipart("/api/bracket/import/confirm").file(csvFile("brackets.csv", csv)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = dataOf(secondResult);
        assertEquals(0, second.path("successCount").asInt());
        assertEquals(2, second.path("failedCount").asInt());
        assertEquals(2, second.path("skippedCount").asInt(), "已存在型号应跳过，允许继续");
        // 失败行文件包含失败与跳过全部 4 行
        assertEquals(4, second.path("failedRows").size());

        // 5) 统计：新增支架均未绑定
        MvcResult statsResult = mockMvc.perform(get("/api/bracket/stats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stats = dataOf(statsResult);
        assertEquals(2, stats.path("unbound").asInt());
        assertEquals(0, stats.path("bound").asInt());

        // 6) 热门型号建议包含导入的新型号
        MvcResult modelsResult = mockMvc.perform(get("/api/bracket/models"))
                .andExpect(status().isOk())
                .andReturn();
        String models = modelsResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(models.contains("ST-INT-A"));
        assertTrue(models.contains("ST-INT-B"));
    }

    @Test
    void xlsxUpload_isParsedAndImported() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("支架");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("支架名称");
            header.createCell(1).setCellValue("支架型号");
            header.createCell(2).setCellValue("长(mm)");
            header.createCell(3).setCellValue("宽(mm)");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("Excel支架");
            data.createCell(1).setCellValue("ST-XLSX-1");
            data.createCell(2).setCellValue(280);
            data.createCell(3).setCellValue(140);
            workbook.write(out);
        }
        MockMultipartFile xlsx = new MockMultipartFile(
                "file", "brackets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());

        MvcResult previewResult = mockMvc.perform(multipart("/api/bracket/import/preview").file(xlsx))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode preview = dataOf(previewResult);
        assertEquals(1, preview.path("validCount").asInt());

        // MockMultipartFile 流已被消费，确认阶段重新构造一个相同内容的文件
        MockMultipartFile xlsxAgain = new MockMultipartFile(
                "file", "brackets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
        MvcResult confirmResult = mockMvc.perform(multipart("/api/bracket/import/confirm").file(xlsxAgain))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, dataOf(confirmResult).path("successCount").asInt());
    }

    @Test
    void unsupportedFileType_returnsFailCodeWithoutServerError() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/bracket/import/preview").file(txt))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(500, root.path("code").asInt());
        assertTrue(root.path("message").asText().contains("不支持的文件格式"));
    }
}
