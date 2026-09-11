package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.repository.BracketRepository;
import com.bracket.vo.BracketImportPreviewVO;
import com.bracket.vo.BracketImportResultVO;
import com.bracket.vo.BracketImportRowVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支架批量导入逐行校验测试：必填、尺寸范围、文件内重复型号为失败行；
 * 库内已存在型号仅提示（DUPLICATE）允许继续，确认导入时跳过。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BracketImportServiceTest {

    @Mock
    private BracketRepository bracketRepository;

    @Mock
    private BracketService bracketService;

    private BracketImportService importService;

    private final BracketImportFileParser fileParser = new BracketImportFileParser();

    @BeforeEach
    void setUp() {
        // 使用真实解析器 + Mock 仓库组合
        importService = new BracketImportService(fileParser, bracketRepository, bracketService);
        when(bracketRepository.findByModelIn(anyList())).thenReturn(List.of());
    }

    private MultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private BracketImportPreviewVO preview(String fileName, String content) {
        return importService.preview(csv(fileName, content));
    }

    @Test
    void preview_allValidRows_areImportable() {
        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "A型支架,ST-A001,300,150\n"
                + "B型支架,ST-B002,400.5,200.25\n";

        BracketImportPreviewVO result = preview("ok.csv", csv);

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(2, result.getRows().get(0).getRowNum());
        assertEquals(300.0, result.getRows().get(0).getLength());
        assertEquals(200.25, result.getRows().get(1).getWidth());
    }

    @Test
    void preview_missingRequiredFields_markedInvalidWithReasons() {
        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + ",ST-A001,300,150\n"
                + "B型支架,,300,150\n"
                + "C型支架,ST-C003,,150\n"
                + "D型支架,ST-D004,300,\n";

        BracketImportPreviewVO result = preview("missing.csv", csv);

        assertEquals(4, result.getInvalidCount());
        assertEquals(0, result.getValidCount());
        assertTrue(result.getRows().get(0).getReason().contains("名称不能为空"));
        assertTrue(result.getRows().get(1).getReason().contains("型号不能为空"));
        assertTrue(result.getRows().get(2).getReason().contains("长度不能为空"));
        assertTrue(result.getRows().get(3).getReason().contains("宽度不能为空"));
    }

    @Test
    void preview_outOfRangeAndNonNumericDimensions_markedInvalid() {
        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "零长支架,ST-A,0,150\n"
                + "超长支架,ST-B,999999,150\n"
                + "负宽支架,ST-C,300,-1\n"
                + "非数字,ST-D,abc,150\n";

        BracketImportPreviewVO result = preview("range.csv", csv);

        assertEquals(4, result.getInvalidCount());
        assertTrue(result.getRows().get(0).getReason().contains("超出允许范围"));
        assertTrue(result.getRows().get(1).getReason().contains("超出允许范围"));
        assertTrue(result.getRows().get(2).getReason().contains("超出允许范围"));
        assertTrue(result.getRows().get(3).getReason().contains("格式不正确"));
    }

    @Test
    void preview_duplicateModelWithinFile_onlyFirstRowValid() {
        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "第一条,ST-DUP,300,150\n"
                + "第二条,ST-DUP,310,160\n"
                + "第三条,ST-DUP,320,170\n";

        BracketImportPreviewVO result = preview("dup.csv", csv);

        assertEquals(1, result.getValidCount());
        assertEquals(2, result.getInvalidCount());
        assertEquals(BracketImportRowVO.STATUS_VALID, result.getRows().get(0).getStatus());
        assertTrue(result.getRows().get(1).getReason().contains("与文件第 2 行重复"));
        assertTrue(result.getRows().get(2).getReason().contains("与文件第 2 行重复"));
    }

    @Test
    void preview_existingModelInDatabase_markedDuplicateButAllowedToContinue() {
        Bracket existing = new Bracket();
        existing.setModel("ST-EXIST");
        when(bracketRepository.findByModelIn(anyList()))
                .thenReturn(List.of(existing));

        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "新支架,ST-NEW,300,150\n"
                + "已有型号支架,ST-EXIST,310,160\n";

        BracketImportPreviewVO result = preview("exist.csv", csv);

        assertEquals(1, result.getValidCount());
        assertEquals(1, result.getDuplicateCount());
        BracketImportRowVO duplicateRow = result.getRows().get(1);
        assertEquals(BracketImportRowVO.STATUS_DUPLICATE, duplicateRow.getStatus());
        assertTrue(duplicateRow.getReason().contains("已存在"));
        // 重复型号不算校验失败
        assertEquals(0, result.getInvalidCount());
    }

    @Test
    void confirm_onlyWritesValidRows_andSkipsDuplicatesAndFailures() {
        Bracket existing = new Bracket();
        existing.setModel("ST-EXIST");
        when(bracketRepository.findByModelIn(anyList()))
                .thenReturn(List.of(existing));
        when(bracketService.saveAllForImport(anyList())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "新支架,ST-NEW,300,150\n"
                + "已有型号,ST-EXIST,310,160\n"
                + "无名称,,320,170\n"
                + "文件重复A,ST-DUP,330,180\n"
                + "文件重复B,ST-DUP,340,190\n";

        BracketImportResultVO result = importService.importBrackets(csv("mix.csv", csv));

        assertEquals(5, result.getTotalCount());
        // 通过且库内不存在：ST-NEW、ST-DUP 首条
        assertEquals(2, result.getSuccessCount());
        // 校验失败：无名称行、文件内重复第二条
        assertEquals(2, result.getFailedCount());
        // 库内已有型号跳过
        assertEquals(1, result.getSkippedCount());
        // 失败行文件包含所有未入库行（失败 + 跳过）
        assertEquals(3, result.getFailedRows().size());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Bracket>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(bracketService).saveAllForImport(captor.capture());
        List<Bracket> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("ST-NEW", saved.get(0).getModel());
        assertEquals("ST-DUP", saved.get(1).getModel());
    }

    @Test
    void confirm_invalidHeader_throwsAndNeverWrites() {
        MultipartFile bad = csv("bad.csv", "错误表头,其他列\n值1,值2\n");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.importBrackets(bad));
        assertTrue(ex.getMessage().contains("表头缺少必要列"));
        verify(bracketService, never()).saveAllForImport(anyList());
    }

    @Test
    void preview_blankRowsAreIgnored_andRowNumbersMatchPhysicalLines() {
        String csv = "支架名称,支架型号,长(mm),宽(mm)\n"
                + "第一条,ST-A,300,150\n"
                + "\n"
                + "   \n"
                + "第二条,ST-B,310,160\n";

        BracketImportPreviewVO result = preview("blank.csv", csv);

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getRows().get(0).getRowNum());
        assertEquals(5, result.getRows().get(1).getRowNum());
    }

    @Test
    void preview_unsupportedFileType_throws() {
        MultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.preview(txt));
        assertTrue(ex.getMessage().contains("不支持的文件格式"));
    }

    @Test
    void preview_headerAliasesAccepted() {
        String csv = "名称,型号,长度（mm）,宽度（mm）\n"
                + "支架A,ST-A,300,150\n";

        BracketImportPreviewVO result = preview("alias.csv", csv);

        assertEquals(1, result.getValidCount());
        assertEquals("支架A", result.getRows().get(0).getName());
    }
}
