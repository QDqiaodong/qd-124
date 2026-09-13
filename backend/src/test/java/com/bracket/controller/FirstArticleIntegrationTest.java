package com.bracket.controller;

import com.bracket.entity.Equipment;
import com.bracket.entity.FirstArticleInspection;
import com.bracket.entity.MoldBatchRecord;
import com.bracket.repository.EquipmentRepository;
import com.bracket.repository.FirstArticleInspectionRepository;
import com.bracket.repository.MoldBatchRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首件尺寸确认单端到端测试：
 * 1. 换模后未登记当前批次的机台不能开单；开单时批次号/型号快照落库；
 * 2. 量差 = 实测 - 标准，任一维度 |量差| > 公差 即超线；超线单不能放行，只能退回再量；
 * 3. 合格单签放需写签放人，签放时间留痕；已放行/已退回为终态，不可再改；
 * 4. 列表可按机台与放行结果筛选，详情可看到谁签的、几点签的；
 * 5. 记录落库持久保存，不随页面关闭丢失。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirstArticleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private MoldBatchRecordRepository moldBatchRecordRepository;

    @Autowired
    private FirstArticleInspectionRepository inspectionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUp() {
        inspectionRepository.deleteAll();
        moldBatchRecordRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    private Equipment saveEquipment(String code) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentName(code + "号封口机");
        e.setAllowedMoldModels("MD-A,MD-B");
        return equipmentRepository.save(e);
    }

    private MoldBatchRecord saveCurrentBatch(Long equipmentId, String batchNo, String moldModel) {
        MoldBatchRecord record = new MoldBatchRecord();
        record.setEquipmentId(equipmentId);
        record.setBatchNo(batchNo);
        record.setMoldModel(moldModel);
        record.setChangeTime(LocalDateTime.now());
        return moldBatchRecordRepository.save(record);
    }

    private String createJson(long equipmentId, String measuredLength, String measuredWidth, String measuredHeight) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("equipmentId", equipmentId);
        node.put("standardLength", 300);
        node.put("standardWidth", 150);
        node.put("standardHeight", 80);
        node.put("tolerance", 0.5);
        node.put("measuredLength", new BigDecimal(measuredLength));
        node.put("measuredWidth", new BigDecimal(measuredWidth));
        node.put("measuredHeight", new BigDecimal(measuredHeight));
        node.put("operator", "王调度");
        return node.toString();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return body(result).path("data");
    }

    private boolean ok(MvcResult result) throws Exception {
        return body(result).path("code").asInt() == 200;
    }

    private long createForm(long equipmentId, String l, String w, String h) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(equipmentId, l, w, h)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(ok(result), "开单应成功: " + body(result).path("message").asText());
        return data(result).path("id").asLong();
    }

    @Test
    void create_blockedWhenNoCurrentBatch_andSnapshotsBatchOnSuccess() throws Exception {
        Equipment noBatch = saveEquipment("FK-F1");
        // 未登记当前模具批次：不能开首件确认单
        MvcResult blocked = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(noBatch.getId(), "300.10", "150.10", "80.10")))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(ok(blocked));
        assertTrue(body(blocked).path("message").asText().contains("尚未登记当前模具批次"));
        assertEquals(0, inspectionRepository.count());

        // 登记当前批次后开单成功，批次号/型号快照落库
        saveCurrentBatch(noBatch.getId(), "MB-FA-1", "MD-A");
        MvcResult created = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(noBatch.getId(), "300.10", "150.10", "80.10")))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(ok(created));
        JsonNode vo = data(created);
        assertEquals("MB-FA-1", vo.path("batchNo").asText());
        assertEquals("MD-A", vo.path("moldModel").asText());
        assertEquals("PENDING", vo.path("status").asText());
        assertEquals("王调度", vo.path("operator").asText());
        assertFalse(vo.path("formNo").asText().isEmpty());
        assertTrue(vo.path("formNo").asText().startsWith("FA-"));
        assertFalse(vo.path("outOfTolerance").asBoolean());
        // 量差 = 实测 - 标准
        assertEquals(0.10, vo.path("lengthDeviation").asDouble(), 0.001);
        assertEquals(0.10, vo.path("widthDeviation").asDouble(), 0.001);
        assertEquals(0.10, vo.path("heightDeviation").asDouble(), 0.001);
    }

    @Test
    void create_validatesRequiredFields() throws Exception {
        Equipment e = saveEquipment("FK-F2");
        saveCurrentBatch(e.getId(), "MB-FA-2", "MD-A");

        // 缺开单人
        ObjectNode noOperator = objectMapper.createObjectNode();
        noOperator.put("equipmentId", e.getId());
        noOperator.put("standardLength", 300);
        noOperator.put("standardWidth", 150);
        noOperator.put("standardHeight", 80);
        noOperator.put("tolerance", 0.5);
        noOperator.put("measuredLength", 300.1);
        noOperator.put("measuredWidth", 150.1);
        noOperator.put("measuredHeight", 80.1);
        MvcResult r1 = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON).content(noOperator.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(r1));
        assertTrue(body(r1).path("message").asText().contains("开单人"));

        // 缺实测高
        ObjectNode noHeight = noOperator.deepCopy();
        noHeight.put("operator", "王调度");
        noHeight.remove("measuredHeight");
        MvcResult r2 = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON).content(noHeight.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(r2));
        assertTrue(body(r2).path("message").asText().contains("实测高"));

        // 公差必须大于 0
        ObjectNode badTolerance = noOperator.deepCopy();
        badTolerance.put("operator", "王调度");
        badTolerance.put("tolerance", 0);
        MvcResult r3 = mockMvc.perform(post("/api/first-articles")
                        .contentType(MediaType.APPLICATION_JSON).content(badTolerance.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(r3));
        assertEquals(0, inspectionRepository.count());
    }

    @Test
    void outOfToleranceForm_cannotRelease_onlyReturn() throws Exception {
        Equipment e = saveEquipment("FK-F3");
        saveCurrentBatch(e.getId(), "MB-FA-3", "MD-A");

        // 长量差 0.74 > 公差 0.5：超线
        long formId = createForm(e.getId(), "300.74", "150.10", "79.90");
        MvcResult detail = mockMvc.perform(get("/api/first-articles/" + formId))
                .andExpect(status().isOk()).andReturn();
        assertTrue(data(detail).path("outOfTolerance").asBoolean());

        // 超线单签放被硬拦截：提示只能退回再量
        ObjectNode releaseJson = objectMapper.createObjectNode();
        releaseJson.put("signer", "陈检");
        MvcResult releaseBlocked = mockMvc.perform(post("/api/first-articles/" + formId + "/release")
                        .contentType(MediaType.APPLICATION_JSON).content(releaseJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(releaseBlocked));
        assertTrue(body(releaseBlocked).path("message").asText().contains("退回再量"));
        assertEquals(FirstArticleInspection.STATUS_PENDING,
                inspectionRepository.findById(formId).orElseThrow().getStatus());

        // 退回再量：写下退回人与退回时间
        ObjectNode returnJson = objectMapper.createObjectNode();
        returnJson.put("operator", "李工");
        returnJson.put("reason", "长量差超线，退回再量");
        MvcResult returned = mockMvc.perform(post("/api/first-articles/" + formId + "/return")
                        .contentType(MediaType.APPLICATION_JSON).content(returnJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ok(returned));
        assertEquals("RETURNED", data(returned).path("status").asText());
        assertEquals("李工", data(returned).path("returnOperator").asText());
        assertFalse(data(returned).path("returnTime").isNull());

        // 退回为终态：不能再签放，也不能再退回
        MvcResult releaseAfterReturn = mockMvc.perform(post("/api/first-articles/" + formId + "/release")
                        .contentType(MediaType.APPLICATION_JSON).content(releaseJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(releaseAfterReturn));
        MvcResult returnAgain = mockMvc.perform(post("/api/first-articles/" + formId + "/return")
                        .contentType(MediaType.APPLICATION_JSON).content(returnJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(returnAgain));
    }

    @Test
    void qualifiedForm_releaseRecordsSignerAndTime_andIsFinal() throws Exception {
        Equipment e = saveEquipment("FK-F4");
        saveCurrentBatch(e.getId(), "MB-FA-4", "MD-A");

        long formId = createForm(e.getId(), "300.12", "149.95", "80.08");

        // 签放人必填
        MvcResult noSigner = mockMvc.perform(post("/api/first-articles/" + formId + "/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("signer", "  ").toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(noSigner));

        // 合格单签放成功：签放人 + 签放时间留痕
        ObjectNode releaseJson = objectMapper.createObjectNode();
        releaseJson.put("signer", "陈检");
        MvcResult released = mockMvc.perform(post("/api/first-articles/" + formId + "/release")
                        .contentType(MediaType.APPLICATION_JSON).content(releaseJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ok(released));
        assertEquals("RELEASED", data(released).path("status").asText());
        assertEquals("陈检", data(released).path("releaseSigner").asText());
        assertFalse(data(released).path("releaseTime").isNull());

        // 已放行为终态：不能再退回/再签放
        ObjectNode returnJson = objectMapper.createObjectNode();
        returnJson.put("operator", "李工");
        MvcResult returnAfterRelease = mockMvc.perform(post("/api/first-articles/" + formId + "/return")
                        .contentType(MediaType.APPLICATION_JSON).content(returnJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(returnAfterRelease));

        // 详情可查到谁签的、几点签的（关掉页面再进记录仍在库中）
        MvcResult detail = mockMvc.perform(get("/api/first-articles/" + formId))
                .andExpect(status().isOk()).andReturn();
        assertEquals("陈检", data(detail).path("releaseSigner").asText());
        assertFalse(data(detail).path("releaseTime").isNull());
        assertEquals("王调度", data(detail).path("operator").asText());
    }

    @Test
    void boundaryDeviation_exactlyOnToleranceLine_isNotOver() throws Exception {
        Equipment e = saveEquipment("FK-F5");
        saveCurrentBatch(e.getId(), "MB-FA-5", "MD-A");

        // 量差恰好等于公差（0.50）：不算超线，可放行
        long formId = createForm(e.getId(), "300.50", "149.50", "80.00");
        MvcResult detail = mockMvc.perform(get("/api/first-articles/" + formId))
                .andExpect(status().isOk()).andReturn();
        assertFalse(data(detail).path("outOfTolerance").asBoolean());
        assertEquals(0.50, data(detail).path("lengthDeviation").asDouble(), 0.001);
        assertEquals(-0.50, data(detail).path("widthDeviation").asDouble(), 0.001);

        ObjectNode releaseJson = objectMapper.createObjectNode();
        releaseJson.put("signer", "陈检");
        MvcResult released = mockMvc.perform(post("/api/first-articles/" + formId + "/release")
                        .contentType(MediaType.APPLICATION_JSON).content(releaseJson.toString()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ok(released));
    }

    @Test
    void list_filtersByEquipmentAndStatus() throws Exception {
        Equipment e1 = saveEquipment("FK-F6");
        saveCurrentBatch(e1.getId(), "MB-FA-6", "MD-A");
        Equipment e2 = saveEquipment("FK-F7");
        saveCurrentBatch(e2.getId(), "MB-FA-7", "MD-B");

        long okForm = createForm(e1.getId(), "300.10", "150.10", "80.10");
        createForm(e1.getId(), "301.00", "150.00", "80.00"); // 超线，保持待签放
        createForm(e2.getId(), "300.10", "150.10", "80.10"); // 另一台机

        // 放行 e1 的合格单
        ObjectNode releaseJson = objectMapper.createObjectNode();
        releaseJson.put("signer", "陈检");
        mockMvc.perform(post("/api/first-articles/" + okForm + "/release")
                        .contentType(MediaType.APPLICATION_JSON).content(releaseJson.toString()))
                .andExpect(status().isOk());

        // 按机台筛：只剩该机台的单
        MvcResult byEquipment = mockMvc.perform(get("/api/first-articles")
                        .param("equipmentId", String.valueOf(e1.getId()))
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andReturn();
        JsonNode page1 = data(byEquipment);
        assertEquals(2, page1.path("total").asInt());
        for (JsonNode row : page1.path("list")) {
            assertEquals(e1.getId().longValue(), row.path("equipmentId").asLong());
            assertEquals("FK-F6", row.path("equipmentCode").asText());
        }

        // 按放行结果筛：已放行只有 1 张
        MvcResult byStatus = mockMvc.perform(get("/api/first-articles")
                        .param("status", "RELEASED")
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, data(byStatus).path("total").asInt());
        assertEquals(okForm, data(byStatus).path("list").get(0).path("id").asLong());

        // 机台 + 放行结果组合筛：e1 的待签放只剩超线那张
        MvcResult combined = mockMvc.perform(get("/api/first-articles")
                        .param("equipmentId", String.valueOf(e1.getId()))
                        .param("status", "PENDING")
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, data(combined).path("total").asInt());
        assertTrue(data(combined).path("list").get(0).path("outOfTolerance").asBoolean());

        // 非法放行结果被拒绝
        MvcResult badStatus = mockMvc.perform(get("/api/first-articles")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isOk()).andReturn();
        assertFalse(ok(badStatus));
    }
}
