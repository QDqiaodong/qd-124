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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 规则变更影响诊断端到端测试：
 * 诊断不落库、确认保存后规则生效（绑定校验与再次诊断结果一致）、无影响场景。
 * 使用 H2 内存库，不依赖外部 MySQL；Redis 不可用时相关调用被兜底吞掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipmentRuleDiagnosisIntegrationTest {

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

    private Equipment saveEquipment(String code, String name) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentName(name);
        return equipmentRepository.save(e);
    }

    private Bracket saveBracket(String name, String model, double length, double width, Long equipmentId) {
        Bracket b = new Bracket();
        b.setName(name);
        b.setModel(model);
        b.setLengthMm(BigDecimal.valueOf(length));
        b.setWidthMm(BigDecimal.valueOf(width));
        b.setEquipmentId(equipmentId);
        return bracketRepository.save(b);
    }

    private String ruleJson(Integer max, String models,
                            Double minLen, Double maxLen, Double minWid, Double maxWid) throws Exception {
        var node = objectMapper.createObjectNode();
        if (max != null) node.put("maxBrackets", max);
        if (models != null) node.put("allowedModels", models);
        if (minLen != null) node.put("minLength", minLen);
        if (maxLen != null) node.put("maxLength", maxLen);
        if (minWid != null) node.put("minWidth", minWid);
        if (maxWid != null) node.put("maxWidth", maxWid);
        return objectMapper.writeValueAsString(node);
    }

    @AfterEach
    void cleanUp() {
        bracketRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    @Test
    void diagnose_doesNotPersist_andCancelKeepsOldRule() throws Exception {
        Equipment eq = saveEquipment("DIAG-001", "诊断设备");

        // POST 诊断：收紧型号
        MvcResult diagResult = mockMvc.perform(post("/api/equipment/" + eq.getId() + "/rule/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(null, "ONLY-X", null, null, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode diag = dataOf(diagResult);
        assertEquals(0, diag.path("currentCount").asInt());
        assertTrue(diag.path("ruleChanged").asBoolean());

        // 诊断后规则不应落库：设备规则字段仍为空，重新查询同样为空
        Equipment reloaded = equipmentRepository.findById(eq.getId()).orElseThrow();
        assertNull(reloaded.getAllowedModels());

        // 设备详情中仍是未配置状态（模拟「取消」）
        MvcResult listResult = mockMvc.perform(get("/api/equipment/list").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode equipmentNode = objectMapper.readTree(
                        listResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("list");
        JsonNode target = findByCode(equipmentNode, "DIAG-001");
        assertFalse(target.path("ruleConfigured").asBoolean());
    }

    @Test
    void confirmSave_takesEffectAndBindValidationMatchesDiagnosis() throws Exception {
        Equipment eq = saveEquipment("DIAG-002", "保存生效设备");
        Bracket compliant = saveBracket("合规支架", "A-01", 200, 100, eq.getId());
        saveBracket("违规模支架", "B-99", 500, 100, eq.getId());

        // 1) 保存前诊断：1 个存量不合规，需要人工处理
        MvcResult diagResult = mockMvc.perform(post("/api/equipment/" + eq.getId() + "/rule/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(5, "A-01", 100.0, 300.0, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode diag = dataOf(diagResult);
        assertEquals(1, diag.path("existingViolations").size());
        assertEquals(1, diag.path("manualCount").asInt());
        assertEquals(1, diag.path("futureOnlyItems").size());
        String diagnoseReason = diag.path("existingViolations").get(0).path("reasons").get(0).asText();

        // 2) 确认保存
        mockMvc.perform(put("/api/equipment/" + eq.getId() + "/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(5, "A-01", 100.0, 300.0, null, null)))
                .andExpect(status().isOk())
                .andReturn();

        // 3) 刷新后规则详情与保存内容一致
        MvcResult listResult = mockMvc.perform(get("/api/equipment/list").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode target = findByCode(dataOf(listResult).path("list"), "DIAG-002");
        assertTrue(target.path("ruleConfigured").asBoolean());
        assertEquals(5, target.path("maxBrackets").asInt());
        assertEquals("A-01", target.path("allowedModels").get(0).asText());
        assertEquals(100.0, target.path("minLength").asDouble(), 0.001);
        assertEquals(300.0, target.path("maxLength").asDouble(), 0.001);

        // 4) 保存后再次诊断，结论与保存前一致
        MvcResult rediagResult = mockMvc.perform(post("/api/equipment/" + eq.getId() + "/rule/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(5, "A-01", 100.0, 300.0, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rediag = dataOf(rediagResult);
        assertFalse(rediag.path("ruleChanged").asBoolean(), "已保存规则再次诊断应视为未变化");
        assertEquals(1, rediag.path("existingViolations").size());

        // 5) 绑定校验与诊断原因一致：违规支架若重新走绑定校验必然冲突，合规支架通过
        Bracket violating = bracketRepository.findAll().stream()
                .filter(b -> "B-99".equals(b.getModel()))
                .findFirst().orElseThrow();
        MvcResult checkBad = mockMvc.perform(post("/api/binding/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bracketId\":" + violating.getId()
                                + ",\"equipmentId\":" + eq.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode badItem = dataOf(checkBad).path("conflicts").get(0);
        assertFalse(badItem.path("passed").asBoolean());
        assertEquals(diagnoseReason, badItem.path("reason").asText(),
                "绑定校验给出的冲突原因必须与规则变更诊断一致");

        MvcResult checkGood = mockMvc.perform(post("/api/binding/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bracketId\":" + compliant.getId() + ",\"equipmentId\":" + eq.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, dataOf(checkGood).path("conflicts").size());
    }

    @Test
    void looseningRule_noManualHandlingAndNoImpactForUnchangedSubmit() throws Exception {
        Equipment eq = saveEquipment("DIAG-003", "放宽设备");
        eq.setMaxBrackets(1);
        eq.setAllowedModels("A-01");
        equipmentRepository.save(eq);
        saveBracket("支架1", "A-01", 100, 50, eq.getId());

        // 放宽到不限：无人工处理项，规则确有变化所以 noImpact=false
        MvcResult loosen = mockMvc.perform(post("/api/equipment/" + eq.getId() + "/rule/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(null, null, null, null, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode looseDiag = dataOf(loosen);
        assertEquals(0, looseDiag.path("manualCount").asInt());
        assertEquals(1, looseDiag.path("futureOnlyItems").size());
        assertFalse(looseDiag.path("noImpact").asBoolean());

        // 用与当前库中完全一致的规则再诊断：无影响，且保存入口应被前端按 ruleChanged=false 拦截
        MvcResult same = mockMvc.perform(post("/api/equipment/" + eq.getId() + "/rule/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(1, "A-01", null, null, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sameDiag = dataOf(same);
        assertFalse(sameDiag.path("ruleChanged").asBoolean());
        assertTrue(sameDiag.path("noImpact").asBoolean());
    }

    private JsonNode findByCode(JsonNode list, String code) {
        for (JsonNode node : list) {
            if (code.equals(node.path("code").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("未找到设备 " + code);
    }
}
