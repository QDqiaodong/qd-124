package com.bracket.controller;

import com.bracket.entity.Bracket;
import com.bracket.entity.BracketRepairRecord;
import com.bracket.repository.BracketRepairRecordRepository;
import com.bracket.repository.BracketRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支架档案「按当前返修状态筛选」端到端测试：
 * 回库不合格列表只显示当前返修单结论为不合格的支架；旧不合格单被更新的返修单
 * （返修中/合格回库）覆盖后必须立刻消失，已回库合格的支架不能再被带回不合格列表。
 * 过滤参数在翻页/改变每页条数/与名称组合查询时同样生效。使用 H2 内存库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BracketListRepairStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BracketRepository bracketRepository;

    @Autowired
    private BracketRepairRecordRepository repairRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Bracket saveBracket(String name, String model) {
        Bracket b = new Bracket();
        b.setName(name);
        b.setModel(model);
        b.setLengthMm(BigDecimal.valueOf(200));
        b.setWidthMm(BigDecimal.valueOf(100));
        return bracketRepository.save(b);
    }

    private BracketRepairRecord repairRecord(Long bracketId, String repairNo,
                                             LocalDateTime repairTime, Boolean returnResult) {
        BracketRepairRecord r = new BracketRepairRecord();
        r.setBracketId(bracketId);
        r.setRepairNo(repairNo);
        r.setRepairTime(repairTime);
        r.setRepairOperator("送修员");
        if (returnResult != null) {
            r.setReturnResult(returnResult);
            r.setInspector("检验员");
            r.setReturnTime(repairTime.plusHours(1));
        }
        return repairRecordRepository.save(r);
    }

    private JsonNode listData(String params) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bracket/list" + params))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private List<Long> filteredIds(String params) throws Exception {
        JsonNode data = listData(params);
        List<Long> ids = new ArrayList<>();
        data.path("list").forEach(row -> ids.add(row.path("id").asLong()));
        return ids;
    }

    @AfterEach
    void cleanUp() {
        repairRecordRepository.deleteAll();
        bracketRepository.deleteAll();
    }

    @Test
    void unqualifiedFilter_returnsOnlyBracketsWhoseCurrentRecordIsUnqualified() throws Exception {
        Bracket normal = saveBracket("从未返修", "A-01");
        Bracket repairing = saveBracket("返修中", "A-01");
        Bracket unqualified = saveBracket("回库不合格", "A-01");
        Bracket qualified = saveBracket("已回库合格", "A-01");
        repairRecord(repairing.getId(), "RP-ING", LocalDateTime.now().minusDays(3), null);
        repairRecord(unqualified.getId(), "RP-BAD", LocalDateTime.now().minusDays(3), false);
        repairRecord(qualified.getId(), "RP-OK", LocalDateTime.now().minusDays(3), true);

        List<Long> ids = filteredIds("?repairStatus=RETURNED_UNQUALIFIED&pageSize=100");

        assertTrue(ids.contains(unqualified.getId()), "回库不合格支架必须出现在筛选结果中");
        assertFalse(ids.contains(normal.getId()), "从未返修的支架不得出现");
        assertFalse(ids.contains(repairing.getId()), "返修中的支架不得出现");
        assertFalse(ids.contains(qualified.getId()), "已回库合格的支架不得被带回不合格列表");
    }

    @Test
    void unqualifiedFilter_hidesBracketOnceNewerRecordQualifiesOrIsStillRepairing() throws Exception {
        // 先是回库不合格
        Bracket reQualified = saveBracket("重新返修后合格", "A-01");
        repairRecord(reQualified.getId(), "RP-BAD-OLD", LocalDateTime.now().minusDays(10), false);
        Bracket reRepairing = saveBracket("重新返修中", "A-01");
        repairRecord(reRepairing.getId(), "RP-BAD-OLD2", LocalDateTime.now().minusDays(10), false);

        List<Long> beforeIds = filteredIds("?repairStatus=RETURNED_UNQUALIFIED&pageSize=100");
        assertTrue(beforeIds.contains(reQualified.getId()));
        assertTrue(beforeIds.contains(reRepairing.getId()));

        // 重新送修：一台已合格回库（最新单合格），一台还在返修中（最新单未回库）
        repairRecord(reRepairing.getId(), "RP-ING-NEW", LocalDateTime.now().minusDays(2), null);
        repairRecord(reQualified.getId(), "RP-OK-NEW", LocalDateTime.now().minusDays(1), true);

        List<Long> afterIds = filteredIds("?repairStatus=RETURNED_UNQUALIFIED&pageSize=100");
        assertFalse(afterIds.contains(reQualified.getId()),
                "最新返修单合格回库后，旧不合格结论不得再让该支架留在不合格列表");
        assertFalse(afterIds.contains(reRepairing.getId()),
                "最新返修单仍在返修中时，旧不合格结论不得再作为待处理项");

        // 合格/返修中筛选各自能找到它们，说明状态过滤口径跟着当前返修单走
        List<Long> qualifiedIds = filteredIds("?repairStatus=RETURNED_QUALIFIED&pageSize=100");
        assertTrue(qualifiedIds.contains(reQualified.getId()));
        List<Long> repairingIds = filteredIds("?repairStatus=REPAIRING&pageSize=100");
        assertTrue(repairingIds.contains(reRepairing.getId()));
    }

    @Test
    void unqualifiedFilter_combinesWithNameSearchAndSurvivesPagination() throws Exception {
        Bracket target = saveBracket("特殊支架-不合格", "A-01");
        Bracket other = saveBracket("特殊支架-合格", "A-01");
        repairRecord(target.getId(), "RP-T-BAD", LocalDateTime.now().minusDays(2), false);
        repairRecord(other.getId(), "RP-T-OK", LocalDateTime.now().minusDays(2), true);

        String params = "?repairStatus=RETURNED_UNQUALIFIED&name=%E7%89%B9%E6%AE%8A&pageSize=100";
        List<Long> ids = filteredIds(params);
        assertTrue(ids.contains(target.getId()), "名称 + 返修状态组合查询应命中不合格支架");
        assertFalse(ids.contains(other.getId()), "同名片段但合格的支架不得命中");

        // 总数只统计不合格支架，翻页参数不改变过滤口径
        JsonNode page1 = listData("?repairStatus=RETURNED_UNQUALIFIED&pageNum=1&pageSize=1");
        JsonNode page2 = listData("?repairStatus=RETURNED_UNQUALIFIED&pageNum=2&pageSize=1");
        assertTrue(page1.path("total").asInt() >= 1);
        assertFalse(page1.path("list").get(0).path("repairStatus").asText().isEmpty());
        // 第二页的每一行也必须是回库不合格，不能夹进合格行
        page2.path("list").forEach(row ->
                assertTrue("RETURNED_UNQUALIFIED".equals(row.path("repairStatus").asText())));
    }
}
