package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.repository.BracketRepository;
import com.bracket.vo.BracketImportPreviewVO;
import com.bracket.vo.BracketImportResultVO;
import com.bracket.vo.BracketImportRowVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 支架档案批量导入：解析 CSV/Excel → 逐行校验（必填、尺寸范围、文件内重复型号）
 * → 与库内已有型号比对（提示但允许继续）→ 确认后仅写入通过行。
 * 校验逻辑在预览与确认两个阶段共用，确认时重新读取文件、重新查库，避免预览后数据被改动。
 */
@Service
public class BracketImportService {

    /** 尺寸合法范围（mm），与 DECIMAL(10,2) 及实际业务场景匹配 */
    public static final double MIN_DIMENSION_MM = 0.01d;
    public static final double MAX_DIMENSION_MM = 10000d;
    /** 单次导入数据行数上限 */
    public static final int MAX_IMPORT_ROWS = 5000;

    private final BracketImportFileParser fileParser;
    private final BracketRepository bracketRepository;
    private final BracketService bracketService;

    @Autowired
    public BracketImportService(BracketImportFileParser fileParser,
                                BracketRepository bracketRepository,
                                BracketService bracketService) {
        this.fileParser = fileParser;
        this.bracketRepository = bracketRepository;
        this.bracketService = bracketService;
    }

    /**
     * 上传文件后的预览校验，不写库。
     */
    public BracketImportPreviewVO preview(MultipartFile file) {
        List<BracketImportRowVO> rows = validate(file).rows;
        int valid = 0;
        int duplicate = 0;
        int invalid = 0;
        for (BracketImportRowVO row : rows) {
            switch (row.getStatus()) {
                case BracketImportRowVO.STATUS_VALID -> valid++;
                case BracketImportRowVO.STATUS_DUPLICATE -> duplicate++;
                default -> invalid++;
            }
        }
        return new BracketImportPreviewVO(rows.size(), valid, duplicate, invalid, rows);
    }

    /**
     * 确认导入：重新解析并校验，仅保存 VALID 行；已有型号行跳过。
     */
    public BracketImportResultVO importBrackets(MultipartFile file) {
        ValidatedFile validated = validate(file);
        List<Bracket> entities = new ArrayList<>();
        List<BracketImportRowVO> failedRows = new ArrayList<>();
        int failed = 0;
        int skipped = 0;
        for (BracketImportRowVO row : validated.rows) {
            if (BracketImportRowVO.STATUS_VALID.equals(row.getStatus())) {
                Bracket bracket = new Bracket();
                bracket.setName(row.getName());
                bracket.setModel(row.getModel());
                bracket.setLengthMm(BigDecimal.valueOf(row.getLength()).setScale(2, RoundingMode.HALF_UP));
                bracket.setWidthMm(BigDecimal.valueOf(row.getWidth()).setScale(2, RoundingMode.HALF_UP));
                entities.add(bracket);
            } else if (BracketImportRowVO.STATUS_DUPLICATE.equals(row.getStatus())) {
                skipped++;
                failedRows.add(row);
            } else {
                failed++;
                failedRows.add(row);
            }
        }
        List<Bracket> saved = bracketService.saveAllForImport(entities);
        return new BracketImportResultVO(
                validated.rows.size(), saved.size(), failed, skipped, failedRows);
    }

    private static class ValidatedFile {
        private final List<BracketImportRowVO> rows;

        private ValidatedFile(List<BracketImportRowVO> rows) {
            this.rows = rows;
        }
    }

    // ============================================================
    // 逐行校验
    // ============================================================

    private ValidatedFile validate(MultipartFile file) {
        List<BracketImportFileParser.RawRow> rawRows = fileParser.parse(file);
        if (rawRows.isEmpty()) {
            throw new IllegalArgumentException("文件内容为空，请按模板填写后上传");
        }
        Map<String, Integer> headerIndex = resolveHeader(rawRows.get(0));
        int nameIdx = headerIndex.get("name");
        int modelIdx = headerIndex.get("model");
        int lengthIdx = headerIndex.get("length");
        int widthIdx = headerIndex.get("width");

        // 先做文件内校验并收集待查库型号，型号比较不区分大小写（与 MySQL 默认排序规则一致）
        List<BracketImportRowVO> rows = new ArrayList<>();
        Map<String, Integer> firstRowByModel = new LinkedHashMap<>();
        Set<String> modelsToCheck = new HashSet<>();
        int dataRowCount = 0;

        for (int i = 1; i < rawRows.size(); i++) {
            BracketImportFileParser.RawRow raw = rawRows.get(i);
            String name = cell(raw, nameIdx);
            String model = cell(raw, modelIdx);
            String lengthText = cell(raw, lengthIdx);
            String widthText = cell(raw, widthIdx);

            // 完全空白行直接忽略，不计入任何统计
            if (name.isEmpty() && model.isEmpty() && lengthText.isEmpty() && widthText.isEmpty()) {
                continue;
            }
            dataRowCount++;
            if (dataRowCount > MAX_IMPORT_ROWS) {
                throw new IllegalArgumentException("单次最多导入 " + MAX_IMPORT_ROWS + " 行数据，请拆分后再导入");
            }

            String error = validateFields(name, model, lengthText, widthText);
            Double length = error == null ? parseDouble(lengthText) : null;
            Double width = error == null ? parseDouble(widthText) : null;

            String modelKey = model.toLowerCase(Locale.ROOT);
            Integer firstRow = null;
            if (error == null) {
                firstRow = firstRowByModel.get(modelKey);
                if (firstRow == null) {
                    firstRowByModel.put(modelKey, raw.getRowNum());
                    modelsToCheck.add(model);
                } else {
                    error = "型号与文件第 " + firstRow + " 行重复，同一型号只能保留一条";
                }
            }

            BracketImportRowVO row = new BracketImportRowVO(
                    raw.getRowNum(),
                    name.isEmpty() ? null : name,
                    model.isEmpty() ? null : model,
                    length,
                    width,
                    error == null ? BracketImportRowVO.STATUS_VALID : BracketImportRowVO.STATUS_INVALID,
                    error
            );
            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("文件没有数据行，请按模板填写后上传");
        }

        // 一次性查库比对已有型号：校验通过的行中型号已存在则标记为 DUPLICATE（提示但允许继续）
        Set<String> existingModels = loadExistingModels(modelsToCheck);
        for (BracketImportRowVO row : rows) {
            if (BracketImportRowVO.STATUS_VALID.equals(row.getStatus())
                    && existingModels.contains(row.getModel().toLowerCase(Locale.ROOT))) {
                row.setStatus(BracketImportRowVO.STATUS_DUPLICATE);
                row.setReason("型号「" + row.getModel() + "」已存在于支架档案，导入时将跳过该条");
            }
        }
        return new ValidatedFile(rows);
    }

    private String validateFields(String name, String model, String lengthText, String widthText) {
        if (name.isEmpty()) {
            return "支架名称不能为空";
        }
        if (name.length() > 100) {
            return "支架名称过长（最多 100 个字符）";
        }
        if (model.isEmpty()) {
            return "支架型号不能为空";
        }
        if (model.length() > 100) {
            return "支架型号过长（最多 100 个字符）";
        }
        String lengthReason = validateDimension("长度", lengthText);
        if (lengthReason != null) {
            return lengthReason;
        }
        String widthReason = validateDimension("宽度", widthText);
        if (widthReason != null) {
            return widthReason;
        }
        return null;
    }

    private String validateDimension(String label, String text) {
        if (text.isEmpty()) {
            return label + "不能为空";
        }
        Double value = parseDouble(text);
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return label + "格式不正确，应为数字（如 300）";
        }
        if (value < MIN_DIMENSION_MM || value > MAX_DIMENSION_MM) {
            return label + "超出允许范围（" + trimNumber(MIN_DIMENSION_MM) + "~"
                    + trimNumber(MAX_DIMENSION_MM) + "mm）";
        }
        return null;
    }

    private Set<String> loadExistingModels(Set<String> models) {
        if (models.isEmpty()) {
            return Set.of();
        }
        List<Bracket> existing = bracketRepository.findByModelIn(new ArrayList<>(models));
        return existing.stream()
                .map(b -> b.getModel().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Double parseDouble(String text) {
        try {
            // 兼容用户误输入千分位逗号
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String cell(BracketImportFileParser.RawRow raw, int index) {
        if (index < 0 || index >= raw.getCells().size()) {
            return "";
        }
        String value = raw.getCells().get(index);
        return value == null ? "" : value.trim();
    }

    // ============================================================
    // 表头识别（兼容模板表头与常见别名）
    // ============================================================

    private static final Map<String, String> HEADER_ALIASES = new HashMap<>();

    static {
        for (String alias : new String[]{"名称", "支架名称", "name"}) {
            HEADER_ALIASES.put(normalizeHeader(alias), "name");
        }
        for (String alias : new String[]{"型号", "支架型号", "model"}) {
            HEADER_ALIASES.put(normalizeHeader(alias), "model");
        }
        for (String alias : new String[]{"长", "长度", "长(mm)", "长度(mm)", "长（mm）", "长度（mm）",
                "长mm", "长度mm", "length", "lengthmm", "length_mm"}) {
            HEADER_ALIASES.put(normalizeHeader(alias), "length");
        }
        for (String alias : new String[]{"宽", "宽度", "宽(mm)", "宽度(mm)", "宽（mm）", "宽度（mm）",
                "宽mm", "宽度mm", "width", "widthmm", "width_mm"}) {
            HEADER_ALIASES.put(normalizeHeader(alias), "width");
        }
    }

    private Map<String, Integer> resolveHeader(BracketImportFileParser.RawRow headerRow) {
        Map<String, Integer> resolved = new HashMap<>();
        for (int i = 0; i < headerRow.getCells().size(); i++) {
            String key = HEADER_ALIASES.get(normalizeHeader(headerRow.getCells().get(i)));
            if (key != null) {
                // 重复表头以第一次出现为准
                resolved.putIfAbsent(key, i);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String field : new String[]{"name", "model", "length", "width"}) {
            if (!resolved.containsKey(field)) {
                missing.add(switch (field) {
                    case "name" -> "支架名称";
                    case "model" -> "支架型号";
                    case "length" -> "长(mm)";
                    default -> "宽(mm)";
                });
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("表头缺少必要列：" + String.join("、", missing)
                    + "，请使用最新导入模板");
        }
        return resolved;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("\\s+", "");
    }
}
