package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.service.BracketImportService;
import com.bracket.vo.BracketImportPreviewVO;
import com.bracket.vo.BracketImportResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 支架档案批量导入工作台：
 * 1) /preview 上传 CSV/Excel 逐行校验并预览（不写库）；
 * 2) /confirm 重新校验后仅写入通过行，已有型号跳过；
 * 3) /template 下载标准导入模板。
 */
@RestController
@RequestMapping("/api/bracket/import")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BracketImportController {

    private static final Logger log = LoggerFactory.getLogger(BracketImportController.class);

    private final BracketImportService importService;

    @Autowired
    public BracketImportController(BracketImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    public ApiResponse<BracketImportPreviewVO> preview(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("请选择要导入的文件");
        }
        try {
            return ApiResponse.success(importService.preview(file));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("支架批量导入预览失败", e);
            return ApiResponse.fail("文件解析失败，请检查文件格式后重试");
        }
    }

    @PostMapping("/confirm")
    public ApiResponse<BracketImportResultVO> confirm(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("请选择要导入的文件");
        }
        try {
            return ApiResponse.success(importService.importBrackets(file));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("支架批量导入确认失败", e);
            return ApiResponse.fail("导入失败，请检查文件后重试");
        }
    }

    /** 标准 CSV 模板（Excel 可直接打开，UTF-8 BOM 防中文乱码） */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        // UTF-8 BOM 防止 Excel 打开 CSV 时中文表头乱码
        String content = "\uFEFF支架名称,支架型号,长(mm),宽(mm)\n";
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"bracket_import_template.csv\"");
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
