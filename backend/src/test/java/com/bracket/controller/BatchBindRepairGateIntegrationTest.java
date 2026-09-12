package com.bracket.controller;

import com.bracket.entity.Bracket;
import com.bracket.entity.BracketRepairRecord;
import com.bracket.entity.Equipment;
import com.bracket.entity.MoldBatchRecord;
import com.bracket.repository.BracketRepairRecordRepository;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.repository.MoldBatchRecordRepository;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量挂接返修放行闸门端到端测试：
 * 勾选返修中未回库 / 回库不合格的支架点校验时，预检弹窗数据照常返回——
 * 顶部批次信息正常、返修支架逐项判为冲突并写明原因（repairGate.passed=false），
 * 合格回库/从未返修的支架仍可通过；确认接口（含兼容旧接口）重新硬校验，返修项绝不放行。
 * 使用 H2 内存库，不依赖外部 MySQL；Redis 不可用时相关调用被兜底吞掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchBindRepairGateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private BracketRepository bracketRepository;

    @Autowired
    private MoldBatchRecordRepository moldBatchRecordRepository;

    @Autowired
    private BracketRepairRecordRepository repairRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private Equipment saveReadyEquipment(String code, String name) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentName(name);
        e.setAllowedMoldModels("MD-A");
        equipmentRepository.save(e);
        MoldBatchRecord record = new MoldBatchRecord();
        record.setEquipmentId(e.getId());
        record.setBatchNo("MB-" + code);
        record.setMoldModel("MD-A");
        record.setChangeTime(LocalDateTime.now());
        record.setOperator("测试员");
        moldBatchRecordRepository.save(record);
        return e;
    }

    private Bracket saveUnboundBracket(String name) {
        Bracket b = new Bracket();
        b.setName(name);
        b.setModel("A-01");
        b.setLengthMm(BigDecimal.valueOf(200));
        b.setWidthMm(BigDecimal.valueOf(100));
        b.setEquipmentId(null);
        return bracketRepository.save(b);
    }

    /** 送修登记：返修单处于返修中（未写回库结论/检验人）。 */
    private void sendToRepair(Bracket bracket, String repairNo) {
        BracketRepairRecord record = new BracketRepairRecord();
        record.setBracketId(bracket.getId());
        record.setRepairNo(repairNo);
        record.setRepairTime(LocalDateTime.now());
        record.setRepairOperator("送修员");
        repairRecordRepository.save(record);
    }

    /** 回库登记：写下结论与检验人。 */
    private void returnToStore(Bracket bracket, String repairNo, boolean qualified, String inspector) {
        BracketRepairRecord record = new BracketRepairRecord();
        record.setBracketId(bracket.getId());
        record.setRepairNo(repairNo);
        record.setRepairTime(LocalDateTime.now().minusDays(1));
        record.setRepairOperator("送修员");
        record.setReturnResult(qualified);
        record.setInspector(inspector);
        record.setReturnTime(LocalDateTime.now());
        repairRecordRepository.save(record);
    }

    private String batchJson(Long equipmentId, long... bracketIds) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("equipmentId", equipmentId);
        var ids = node.putArray("bracketIds");
        for (long id : bracketIds) {
            ids.add(id);
        }
        return objectMapper.writeValueAsString(node);
    }

    private JsonNode itemOf(JsonNode data, long bracketId) {
        for (JsonNode item : data.path("items")) {
            if (item.path("bracketId").asLong() == bracketId) {
                return item;
            }
        }
        throw new IllegalStateException("未找到支架 " + bracketId + " 的预检条目");
    }

    @AfterEach
    void cleanUp() {
        repairRecordRepository.deleteAll();
        bracketRepository.deleteAll();
        moldBatchRecordRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    @Test
    void batchCheck_blocksRepairingAndUnqualifiedItemsPerRow_withReasons_andKeepsQualifiedItemPassed() throws Exception {
        Equipment target = saveReadyEquipment("FK-RP1", "返修闸门机");
        Bracket normal = saveUnboundBracket("从未返修");
        Bracket repairing = saveUnboundBracket("返修中未回库");
        Bracket unqualified = saveUnboundBracket("回库不合格");
        Bracket qualified = saveUnboundBracket("回库合格");
        sendToRepair(repairing, "RP-ING");
        returnToStore(unqualified, "RP-BAD", false, "检验员甲");
        returnToStore(qualified, "RP-OK", true, "检验员乙");

        MvcResult result = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(target.getId(),
                                normal.getId(), repairing.getId(), unqualified.getId(), qualified.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = dataOf(result);

        // 预检结果弹窗数据照常返回，批次闸门通过后才进入逐项返修判定
        assertTrue(data.path("moldBatchGate").path("passed").asBoolean());
        assertEquals(target.getEquipmentCode(), data.path("equipmentCode").asText());
        assertEquals(2, data.path("passedItems").size());
        assertEquals(2, data.path("conflicts").size());

        JsonNode repairingItem = itemOf(data, repairing.getId());
        assertFalse(repairingItem.path("passed").asBoolean());
        assertFalse(repairingItem.path("repairGate").path("passed").asBoolean());
        assertEquals("REPAIRING", repairingItem.path("repairGate").path("status").asText());
        assertTrue(repairingItem.path("reason").asText().contains("尚未写回库结论与检验人"),
                repairingItem.path("reason").asText());

        JsonNode unqualifiedItem = itemOf(data, unqualified.getId());
        assertFalse(unqualifiedItem.path("passed").asBoolean());
        assertFalse(unqualifiedItem.path("repairGate").path("passed").asBoolean());
        assertEquals("RETURNED_UNQUALIFIED", unqualifiedItem.path("repairGate").path("status").asText());
        assertTrue(unqualifiedItem.path("reason").asText().contains("回库结论为不合格"),
                unqualifiedItem.path("reason").asText());

        // 合格回库与从未返修的支架正常通过
        assertTrue(itemOf(data, normal.getId()).path("passed").asBoolean());
        assertTrue(itemOf(data, qualified.getId()).path("passed").asBoolean());
    }

    @Test
    void batchConfirm_neverBindsRepairBlockedBrackets_evenWhenClientSubmitsThem() throws Exception {
        Equipment target = saveReadyEquipment("FK-RP2", "返修确认机");
        Bracket normal = saveUnboundBracket("可绑定");
        Bracket repairing = saveUnboundBracket("返修中");
        Bracket unqualified = saveUnboundBracket("不合格");
        sendToRepair(repairing, "RP-ING-2");
        returnToStore(unqualified, "RP-BAD-2", false, "检验员丙");

        // 客户端无视预检结果，把返修拦截项也提交确认：后端重新硬校验，仅绑定通过项
        MvcResult result = mockMvc.perform(post("/api/binding/batch-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(target.getId(),
                                normal.getId(), repairing.getId(), unqualified.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = dataOf(result);
        assertEquals(1, data.path("boundCount").asInt());

        assertEquals(target.getId(), bracketRepository.findById(normal.getId()).orElseThrow().getEquipmentId());
        assertNull(bracketRepository.findById(repairing.getId()).orElseThrow().getEquipmentId());
        assertNull(bracketRepository.findById(unqualified.getId()).orElseThrow().getEquipmentId());
    }

    @Test
    void legacyBatchBindEndpoint_alsoEnforcesRepairGate() throws Exception {
        Equipment target = saveReadyEquipment("FK-RP3", "旧接口机");
        Bracket unqualified = saveUnboundBracket("旧接口不合格");
        returnToStore(unqualified, "RP-BAD-3", false, "检验员丁");

        // 兼容旧接口 /binding/batch-bind 同样必须硬拦截返修支架
        mockMvc.perform(post("/api/binding/batch-bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(target.getId(), unqualified.getId())))
                .andExpect(status().isOk());

        assertNull(bracketRepository.findById(unqualified.getId()).orElseThrow().getEquipmentId());
        assertEquals(0, bracketRepository.findByEquipmentId(target.getId()).size());
    }
}
