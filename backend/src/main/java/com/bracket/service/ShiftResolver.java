package com.bracket.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 班次解析：把任意业务时间归属到车间班次，生成当班标识 shiftKey。
 *
 * 默认三班制（可通过 lubrication.shifts 配置，格式「代码:名称:HH:mm」，逗号分隔）：
 * - N 夜班 00:00 起、D 早班 08:00 起、E 晚班 16:00 起。
 * 跨零点的夜班归属开始当天的班次（凌晨 2 点属于「当天夜班」），
 * 换班（如 08:00 进入早班）后上一班的润滑记录 shiftKey 不再匹配，当班挂接立即失放。
 */
@Component
public class ShiftResolver {

    public static class Shift {
        private final String code;
        private final String name;
        private final LocalTime start;

        public Shift(String code, String name, LocalTime start) {
            this.code = code;
            this.name = name;
            this.start = start;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public LocalTime getStart() {
            return start;
        }
    }

    public static class ShiftInfo {
        /** 当班唯一标识：yyyy-MM-dd#班次代码，作为放行比对键 */
        private final String shiftKey;
        private final LocalDate shiftDate;
        private final String shiftCode;
        private final String shiftName;

        public ShiftInfo(String shiftKey, LocalDate shiftDate, String shiftCode, String shiftName) {
            this.shiftKey = shiftKey;
            this.shiftDate = shiftDate;
            this.shiftCode = shiftCode;
            this.shiftName = shiftName;
        }

        public String getShiftKey() {
            return shiftKey;
        }

        public LocalDate getShiftDate() {
            return shiftDate;
        }

        public String getShiftCode() {
            return shiftCode;
        }

        public String getShiftName() {
            return shiftName;
        }

        /** 展示文本：2026-09-12 早班 */
        public String getLabel() {
            return shiftDate.toString() + shiftName;
        }
    }

    private final List<Shift> shifts;

    public ShiftResolver(
            @Value("${lubrication.shifts:N:夜班:00:00,D:早班:08:00,E:晚班:16:00}") String shiftConfig) {
        this.shifts = parse(shiftConfig);
    }

    private static List<Shift> parse(String config) {
        List<Shift> list = new ArrayList<>();
        if (config != null) {
            for (String part : config.split("[,，]")) {
                String[] fields = part.trim().split(":");
                if (fields.length >= 4 && !fields[0].trim().isEmpty()) {
                    LocalTime start = LocalTime.of(Integer.parseInt(fields[2].trim()), Integer.parseInt(fields[3].trim()));
                    list.add(new Shift(fields[0].trim(), fields[1].trim(), start));
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new Shift("N", "夜班", LocalTime.of(0, 0)));
            list.add(new Shift("D", "早班", LocalTime.of(8, 0)));
            list.add(new Shift("E", "晚班", LocalTime.of(16, 0)));
        }
        // 按开班时间排序；不要求必须配置 00:00 班次
        list.sort(Comparator.comparing(Shift::getStart));
        return List.copyOf(list);
    }

    /** 解析指定时间所属班次。 */
    public ShiftInfo resolve(LocalDateTime time) {
        LocalDateTime t = time == null ? LocalDateTime.now() : time;
        LocalTime tod = t.toLocalTime();
        Shift matched = shifts.get(0);
        for (Shift shift : shifts) {
            if (!tod.isBefore(shift.getStart())) {
                matched = shift;
            } else {
                break;
            }
        }
        LocalDate date = t.toLocalDate();
        return new ShiftInfo(date.toString() + "#" + matched.getCode(), date, matched.getCode(), matched.getName());
    }

    /** 服务端当前班次。 */
    public ShiftInfo currentShift() {
        return resolve(LocalDateTime.now());
    }
}
