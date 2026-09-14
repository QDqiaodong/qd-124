package com.bracket.controller;

import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「只看超额」开关端到端测试：
 * 过滤在服务端分页前完成（刷新/翻页名单不丢、与 total 对得上），
 * 且名单中的每台设备卡片 capacityStatus 必须为 exceeded，严格同口径。
 * 使用 H2 内存库（MySQL 兼容模式），不依赖外部 MySQL。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipmentExceededFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private BracketRepository bracketRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private Equipment saveEquipment(String code, String name, Integer maxBrackets, int boundCount) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentName(name);
        e.setMaxBrackets(maxBrackets);
        e = equipmentRepository.save(e);
        for (int i = 0; i < boundCount; i++) {
            Bracket b = new Bracket();
            b.setName(code + "-支架" + i);
            b.setModel("M-" + code);
            b.setLengthMm(BigDecimal.valueOf(100));
            b.setWidthMm(BigDecimal.valueOf(50));
            b.setEquipmentId(e.getId());
            bracketRepository.save(b);
        }
        return e;
    }

    @AfterEach
    void cleanUp() {
        bracketRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    private MvcResult queryList(Boolean onlyExceeded, Integer pageNum, Integer pageSize,
                                String code, String name) throws Exception {
        var builder = get("/api/equipment/list");
        if (onlyExceeded != null) {
            builder.param("onlyExceeded", String.valueOf(onlyExceeded));
        }
        if (pageNum != null) {
            builder.param("pageNum", String.valueOf(pageNum));
        }
        if (pageSize != null) {
            builder.param("pageSize", String.valueOf(pageSize));
        }
        if (code != null) {
            builder.param("code", code);
        }
        if (name != null) {
            builder.param("name", name);
        }
        return mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
    }

    /** code -> capacityStatus，便于断言名单与卡片标记一一对应 */
    private Map<String, String> statusByCode(JsonNode data) {
        Map<String, String> map = new HashMap<>();
        for (JsonNode node : data.path("list")) {
            map.put(node.path("code").asText(), node.path("capacityStatus").asText());
        }
        return map;
    }

    private List<String> codesOf(JsonNode data) {
        List<String> codes = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            codes.add(node.path("code").asText());
        }
        return codes;
    }

    @Test
    void onlyExceededReturnsExactlyOverCapacityMachinesAndTagsMatch() throws Exception {
        // 上限 1 已挂 2 → 超额；上限 2 已挂 2 → 满（不算超额）；上限 3 已挂 1 → 正常；
        // 未配上限挂 2 → 不限（不算超额）
        saveEquipment("EXC-1", "超额机", 1, 2);
        saveEquipment("FULL-1", "已满机", 2, 2);
        saveEquipment("NORMAL-1", "正常机", 3, 1);
        saveEquipment("UNLIMITED-1", "不限机", null, 2);

        JsonNode filtered = dataOf(queryList(true, 1, 50, null, null));
        assertEquals(1, filtered.path("total").asInt(), "超额名单只含严格超出上限的机台");
        Map<String, String> statuses = statusByCode(filtered);
        assertEquals(1, statuses.size());
        assertTrue(statuses.containsKey("EXC-1"));
        // 卡片上的超出容量标记必须和这份名单对得上：名单内必须是 exceeded，满/正常/不限都不能混进来
        assertEquals("exceeded", statuses.get("EXC-1"), "名单中的卡片必须打出「超出容量」标记");
        assertFalse(statuses.containsKey("FULL-1"));
        assertFalse(statuses.containsKey("NORMAL-1"));
        assertFalse(statuses.containsKey("UNLIMITED-1"));

        // 不带开关（默认关闭）回到全量结果
        JsonNode all = dataOf(queryList(null, 1, 50, null, null));
        assertEquals(4, all.path("total").asInt());
        // 全量列表中各卡片容量状态按同一口径给出
        Map<String, String> allStatuses = statusByCode(all);
        assertEquals("exceeded", allStatuses.get("EXC-1"));
        assertEquals("full", allStatuses.get("FULL-1"));
        assertEquals("normal", allStatuses.get("NORMAL-1"));
        assertEquals("unlimited", allStatuses.get("UNLIMITED-1"));
    }

    @Test
    void onlyExceededCombinesWithCodeAndNameSearch() throws Exception {
        saveEquipment("LINE-A-1", "一号线超额机", 1, 2);
        saveEquipment("LINE-A-2", "一号线正常机", 5, 1);
        saveEquipment("LINE-B-1", "二号线超额机", 1, 3);

        // 开关 + 编号搜索：在超额集合里再按编号过滤
        JsonNode byCode = dataOf(queryList(true, 1, 50, "LINE-A", null));
        assertEquals(1, byCode.path("total").asInt());
        assertEquals(List.of("LINE-A-1"), codesOf(byCode));

        // 开关 + 名称搜索
        JsonNode byName = dataOf(queryList(true, 1, 50, null, "二号线"));
        assertEquals(1, byName.path("total").asInt());
        assertEquals(List.of("LINE-B-1"), codesOf(byName));

        // 搜索无匹配时返回空，而不是退回全量超额名单
        JsonNode none = dataOf(queryList(true, 1, 50, "NOT-EXIST", null));
        assertEquals(0, none.path("total").asInt());
        assertEquals(0, none.path("list").size());
    }

    @Test
    void onlyExceededIsServerSidePaginatedAndStableOnRefresh() throws Exception {
        // 5 台超额机，每页 2 条：服务端分页，翻页/刷新名单连续不重不漏
        for (int i = 1; i <= 5; i++) {
            saveEquipment(String.format("PAGE-%02d", i), "超额分页机" + i, 1, 2);
        }
        saveEquipment("PAGE-OK", "正常机", 9, 0);

        JsonNode page1 = dataOf(queryList(true, 1, 2, null, null));
        JsonNode page2 = dataOf(queryList(true, 2, 2, null, null));
        JsonNode page3 = dataOf(queryList(true, 3, 2, null, null));

        assertEquals(5, page1.path("total").asInt(), "total 是全部超额机数量，与页大小无关");
        assertEquals(2, page1.path("list").size());
        assertEquals(2, page2.path("list").size());
        assertEquals(1, page3.path("list").size());

        List<String> seen = new ArrayList<>();
        seen.addAll(codesOf(page1));
        seen.addAll(codesOf(page2));
        seen.addAll(codesOf(page3));
        assertEquals(5, seen.size(), "三页合计正好 5 台");
        assertEquals(5, seen.stream().distinct().count(), "翻页无重复无丢失");
        assertFalse(seen.contains("PAGE-OK"), "正常机不会混进超额名单");

        // 模拟刷新：同样的参数再请求一次，第一页结果不变
        JsonNode refreshed = dataOf(queryList(true, 1, 2, null, null));
        assertEquals(codesOf(page1), codesOf(refreshed), "刷新后超额机仍在，且顺序一致");

        // 每一页名单里的卡片都必须带着「超出容量」标记
        for (JsonNode node : refreshed.path("list")) {
            assertEquals("exceeded", node.path("capacityStatus").asText());
        }
    }
}
