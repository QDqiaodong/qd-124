package com.bracket.controller;

import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.entity.MoldBatchRecord;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 换模批次登记与放行联锁端到端测试：
 * 1. 换模后必须写当前批次，型号不在允许清单不能写入；
 * 2. 未写批次/批次不合规时批量挂接与换线改挂整单拦截；
 * 3. 放行只认最新批次，换新批次后旧记录不再作为放行依据；
 * 4. 历史换模记录可按设备倒序翻看，仅第一条标记为当前批次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoldBatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private BracketRepository bracketRepository;

    @Autowired
    private MoldBatchRecordRepository moldBatchRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUp() {
        bracketRepository.deleteAll();
        moldBatchRecordRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    private Equipment saveEquipment(String code, String allowedMoldModels) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentName(code + "号封口机");
        e.setAllowedMoldModels(allowedMoldModels);
        return equipmentRepository.save(e);
    }

    private Bracket saveUnboundBracket(String model) {
        Bracket b = new Bracket();
        b.setName("支架-" + model);
        b.setModel(model);
        b.setLengthMm(BigDecimal.valueOf(200));
        b.setWidthMm(BigDecimal.valueOf(100));
        return bracketRepository.save(b);
    }

    private String registerJson(String batchNo, String moldModel) {
        var node = objectMapper.createObjectNode();
        node.put("batchNo", batchNo);
        node.put("moldModel", moldModel);
        node.put("operator", "测试员");
        return node.toString();
    }

    private String batchBindJson(long equipmentId, long... bracketIds) {
        var node = objectMapper.createObjectNode();
        node.put("equipmentId", equipmentId);
        var ids = node.putArray("bracketIds");
        for (long id : bracketIds) {
            ids.add(id);
        }
        return node.toString();
    }

    private String rehangJson(Long sourceId, Long targetId, long... bracketIds) {
        var node = objectMapper.createObjectNode();
        node.put("sourceEquipmentId", sourceId);
        node.put("targetEquipmentId", targetId);
        var ids = node.putArray("bracketIds");
        for (long id : bracketIds) {
            ids.add(id);
        }
        return node.toString();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return body(result).path("data");
    }

    @Test
    void register_rejectsMissingFieldsAndModelNotAllowed() throws Exception {
        Equipment e = saveEquipment("FK-M1", "MD-A,MD-B");

        // 型号不在允许清单：写入失败，不产生记录
        MvcResult reject = mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("MB-X", "MD-FORBIDDEN")))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(body(reject).path("code").asText().equals("200"));
        assertTrue(body(reject).path("message").asText().contains("不在该机允许清单内"));
        assertEquals(0, moldBatchRecordRepository.findByEquipmentIdOrderByChangeTimeDescIdDesc(e.getId()).size());

        // 批次号为空：写入失败
        MvcResult noBatch = mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("  ", "MD-A")))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(body(noBatch).path("code").asText().equals("200"));

        // 设备未配置允许清单：任何登记都拒绝
        Equipment noList = saveEquipment("FK-M2", null);
        MvcResult noListResult = mockMvc.perform(post("/api/equipment/" + noList.getId() + "/mold-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("MB-Y", "MD-A")))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(body(noListResult).path("message").asText().contains("尚未维护允许模具型号清单"));
    }

    @Test
    void register_successRecordsCurrentBatch_andEquipmentListExposesIt() throws Exception {
        Equipment e = saveEquipment("FK-M3", "MD-A,MD-B");

        MvcResult result = mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("MB-OK-1", "MD-A")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode vo = data(result);
        assertEquals("MB-OK-1", vo.path("batchNo").asText());
        assertEquals("MD-A", vo.path("moldModel").asText());
        assertTrue(vo.path("current").asBoolean());

        // 设备配套清单返回当前批次与放行就绪标记
        MvcResult all = mockMvc.perform(get("/api/equipment/all")).andExpect(status().isOk()).andReturn();
        JsonNode target = null;
        for (JsonNode node : data(all)) {
            if (node.path("id").asLong() == e.getId()) {
                target = node;
            }
        }
        assertTrue(target != null);
        assertEquals("MB-OK-1", target.path("currentBatchNo").asText());
        assertEquals("MD-A", target.path("currentMoldModel").asText());
        assertTrue(target.path("moldBatchReady").asBoolean());
    }

    @Test
    void batchBind_blockedWhenNoBatch_andReleasedAfterRegister() throws Exception {
        Equipment e = saveEquipment("FK-M4", "MD-A");
        Bracket b = saveUnboundBracket("ANY");

        // 未写当前批次：批量挂接预检整单拦截
        MvcResult blocked = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode blockedData = data(blocked);
        assertEquals(0, blockedData.path("passedItems").size());
        assertEquals(1, blockedData.path("conflicts").size());
        assertFalse(blockedData.path("moldBatchGate").path("passed").asBoolean());
        assertTrue(blockedData.path("conflicts").get(0).path("reason").asText().contains("尚未登记当前模具批次"));

        // 确认接口同样拦截：支架仍未绑定
        mockMvc.perform(post("/api/binding/batch-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk());
        assertEquals(null, bracketRepository.findById(b.getId()).orElseThrow().getEquipmentId());

        // 登记当前批次后放行，确认绑定成功
        mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("MB-GO", "MD-A"))).andExpect(status().isOk());

        MvcResult ok = mockMvc.perform(post("/api/binding/batch-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, data(ok).path("boundCount").asInt());
        assertEquals(e.getId(), bracketRepository.findById(b.getId()).orElseThrow().getEquipmentId());
    }

    @Test
    void batchBind_blockedWhenCurrentBatchModelNotAllowed() throws Exception {
        Equipment e = saveEquipment("FK-M5", "MD-A");
        Bracket b = saveUnboundBracket("ANY");
        // 设备曾登记过 MD-A，但允许清单现在只剩 MD-A，当前批次却是 MD-B（直接构造不合规在机批次）
        MoldBatchRecord stale = new MoldBatchRecord();
        stale.setEquipmentId(e.getId());
        stale.setBatchNo("MB-BAD");
        stale.setMoldModel("MD-B");
        stale.setChangeTime(LocalDateTime.now());
        moldBatchRecordRepository.save(stale);

        MvcResult blocked = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode gate = data(blocked).path("moldBatchGate");
        assertFalse(gate.path("passed").asBoolean());
        assertEquals("MB-BAD", gate.path("currentBatchNo").asText());
        assertTrue(gate.path("reason").asText().contains("不在该机允许清单内"));
        assertEquals(0, data(blocked).path("passedItems").size());
    }

    @Test
    void afterRegisteringNewBatch_oldBatchNoLongerGrantsRelease() throws Exception {
        Equipment e = saveEquipment("FK-M6", "MD-A,MD-B");
        Bracket b = saveUnboundBracket("ANY");

        // 旧批次 MD-A 合规时放行
        mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("MB-OLD", "MD-A"))).andExpect(status().isOk());
        MvcResult first = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(data(first).path("moldBatchGate").path("passed").asBoolean());
        assertEquals("MB-OLD", data(first).path("moldBatchGate").path("currentBatchNo").asText());

        // 换新批次：清单改为只允许 MD-C 之外，当前登记 MD-B；旧记录 MB-OLD 保留但不再是放行依据
        // 先收紧清单（不含 MD-A），模拟换模同时工艺调整
        e.setAllowedMoldModels("MD-B");
        equipmentRepository.save(e);
        mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("MB-NEW", "MD-B"))).andExpect(status().isOk());

        MvcResult second = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode gate = data(second).path("moldBatchGate");
        assertTrue(gate.path("passed").asBoolean());
        assertEquals("MB-NEW", gate.path("currentBatchNo").asText());

        // 再登记一个不合规？不允许写入。构造旧批次仍合规但被更新批次顶替的场景：
        // 登记 MB-NEW2 后，MB-NEW 即使型号仍在清单，也不再是「当前批次」——这里验证取最新一条
        mockMvc.perform(post("/api/equipment/" + e.getId() + "/mold-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("MB-NEW2", "MD-B"))).andExpect(status().isOk());
        MvcResult third = mockMvc.perform(post("/api/binding/batch-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBindJson(e.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("MB-NEW2", data(third).path("moldBatchGate").path("currentBatchNo").asText());
        // 历史记录仍为 3 条
        assertEquals(3, moldBatchRecordRepository.findByEquipmentIdOrderByChangeTimeDescIdDesc(e.getId()).size());
    }

    @Test
    void rehang_blockedByTargetBatchGate() throws Exception {
        Equipment source = saveEquipment("FK-SRC", null);
        Equipment target = saveEquipment("FK-TGT", "MD-A");
        Bracket onSource = saveUnboundBracket("ANY");
        onSource.setEquipmentId(source.getId());
        bracketRepository.save(onSource);

        // 目标机未写批次：改挂预检与确认都拦截，支架不动
        MvcResult check = mockMvc.perform(post("/api/binding/rehang-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(), onSource.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode checkData = data(check);
        assertEquals(0, checkData.path("passedItems").size());
        assertTrue(checkData.path("conflicts").get(0).path("reason").asText().contains("尚未登记当前模具批次"));
        assertFalse(checkData.path("moldBatchGate").path("passed").asBoolean());

        MvcResult confirm = mockMvc.perform(post("/api/binding/rehang-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(), onSource.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, data(confirm).path("rehungCount").asInt());
        assertEquals(source.getId(), bracketRepository.findById(onSource.getId()).orElseThrow().getEquipmentId());

        // 目标机登记当前批次后改挂放行
        mockMvc.perform(post("/api/equipment/" + target.getId() + "/mold-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("MB-RH", "MD-A"))).andExpect(status().isOk());
        MvcResult ok = mockMvc.perform(post("/api/binding/rehang-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(), onSource.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, data(ok).path("rehungCount").asInt());
        assertEquals(target.getId(), bracketRepository.findById(onSource.getId()).orElseThrow().getEquipmentId());
    }

    @Test
    void history_isPerEquipment_newestFirst_andOnlyFirstMarkedCurrent() throws Exception {
        Equipment e = saveEquipment("FK-H1", "MD-A,MD-B");
        Equipment other = saveEquipment("FK-H2", "MD-A");

        // 手动插入不同换模时间的两条记录
        MoldBatchRecord older = new MoldBatchRecord();
        older.setEquipmentId(e.getId());
        older.setBatchNo("MB-H-OLD");
        older.setMoldModel("MD-A");
        older.setChangeTime(LocalDateTime.now().minusDays(10));
        moldBatchRecordRepository.save(older);
        MoldBatchRecord newer = new MoldBatchRecord();
        newer.setEquipmentId(e.getId());
        newer.setBatchNo("MB-H-NEW");
        newer.setMoldModel("MD-B");
        newer.setChangeTime(LocalDateTime.now());
        moldBatchRecordRepository.save(newer);
        // 另一台设备的批次不能串到本机历史
        MoldBatchRecord otherRecord = new MoldBatchRecord();
        otherRecord.setEquipmentId(other.getId());
        otherRecord.setBatchNo("MB-OTHER");
        otherRecord.setMoldModel("MD-A");
        otherRecord.setChangeTime(LocalDateTime.now());
        moldBatchRecordRepository.save(otherRecord);

        MvcResult result = mockMvc.perform(get("/api/equipment/" + e.getId() + "/mold-batches")
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page = data(result);
        assertEquals(2, page.path("total").asInt());
        JsonNode rows = page.path("list");
        assertEquals("MB-H-NEW", rows.get(0).path("batchNo").asText());
        assertTrue(rows.get(0).path("current").asBoolean());
        assertEquals("MB-H-OLD", rows.get(1).path("batchNo").asText());
        assertFalse(rows.get(1).path("current").asBoolean());

        // 不存在的设备返回失败
        MvcResult missing = mockMvc.perform(get("/api/equipment/999999/mold-batches"))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(body(missing).path("code").asText().equals("200"));
    }

    @Test
    void singleBind_isNotBlockedByMoldBatchGate() throws Exception {
        // 单个绑定不在换模批次联锁范围内：未写批次仍可按型号/尺寸规则绑定
        Equipment e = saveEquipment("FK-S1", "MD-A");
        Bracket b = saveUnboundBracket("ANY");

        MvcResult result = mockMvc.perform(post("/api/binding/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("bracketId", b.getId())
                                .put("equipmentId", e.getId()).toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, data(result).path("boundCount").asInt());
        assertEquals(e.getId(), bracketRepository.findById(b.getId()).orElseThrow().getEquipmentId());
    }
}
