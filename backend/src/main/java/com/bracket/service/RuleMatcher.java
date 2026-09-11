package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备配套规则匹配器：绑定校验与规则变更影响诊断共用同一套判定，
 * 保证「保存后绑定校验」与「保存前诊断」给出的结论和原因完全一致。
 * 规则字段为空表示该项不限制；设备未配置任何规则时全部放行。
 */
@Component
public class RuleMatcher {

    /**
     * 校验单个支架命中的全部规则冲突原因，按型号、长度、宽度顺序聚合。
     * 无冲突时返回空列表。
     */
    public List<String> violations(Equipment equipment, Bracket bracket) {
        List<String> reasons = new ArrayList<>();
        List<String> allowedModels = EquipmentService.parseModels(equipment.getAllowedModels());
        if (!allowedModels.isEmpty() && !allowedModels.contains(bracket.getModel())) {
            reasons.add("型号不在允许范围内（允许：" + String.join("、", allowedModels) + "）");
        }
        String lengthReason = checkDimension("长度", bracket.getLengthMm(),
                equipment.getMinLength(), equipment.getMaxLength());
        if (lengthReason != null) {
            reasons.add(lengthReason);
        }
        String widthReason = checkDimension("宽度", bracket.getWidthMm(),
                equipment.getMinWidth(), equipment.getMaxWidth());
        if (widthReason != null) {
            reasons.add(widthReason);
        }
        return reasons;
    }

    /**
     * 绑定校验入口：只需要第一条冲突原因；通过时返回 null，与历史提示文案保持一致。
     */
    public String matchRule(Equipment equipment, Bracket bracket) {
        List<String> reasons = violations(equipment, bracket);
        return reasons.isEmpty() ? null : reasons.get(0);
    }

    private String checkDimension(String label, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (value == null) {
            return label + "尺寸缺失，无法满足" + formatRange(label, min, max);
        }
        if (min != null && value.compareTo(min) < 0) {
            return label + "低于允许范围（" + formatRange(label, min, max) + "）";
        }
        if (max != null && value.compareTo(max) > 0) {
            return label + "超出允许范围（" + formatRange(label, min, max) + "）";
        }
        return null;
    }

    private String formatRange(String label, BigDecimal min, BigDecimal max) {
        String minText = min != null ? min.stripTrailingZeros().toPlainString() : "不限";
        String maxText = max != null ? max.stripTrailingZeros().toPlainString() : "不限";
        return label + minText + "~" + maxText + "mm";
    }
}
