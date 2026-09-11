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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 换线改挂端到端测试：
 * 预检按目标机型号/长宽/容量逐项判定；确认后通过项一次性改挂、全程保持已绑定，
 * 冲突项仍留在源设备；源/目标设备占用与未绑定统计刷新后一致。
 * 使用 H2 内存库，不依赖外部 MySQL；Redis 不可用时相关调用被兜底吞掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RehangIntegrationTest {

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

    private String rehangJson(Long sourceId, Long targetId, long... bracketIds) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("sourceEquipmentId", sourceId);
        node.put("targetEquipmentId", targetId);
        var ids = node.putArray("bracketIds");
        for (long id : bracketIds) {
            ids.add(id);
        }
        return objectMapper.writeValueAsString(node);
    }

    @AfterEach
    void cleanUp() {
        bracketRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    @Test
    void precheck_classifiesByTargetModelDimensionAndCapacity() throws Exception {
        Equipment source = saveEquipment("SRC-001", "一号封口机");
        Equipment target = saveEquipment("TGT-001", "二号封口机");
        // 目标机：容量 1、只允许 A-01、长度 100~300
        target.setMaxBrackets(1);
        target.setAllowedModels("A-01");
        target.setMinLength(BigDecimal.valueOf(100));
        target.setMaxLength(BigDecimal.valueOf(300));
        equipmentRepository.save(target);

        Bracket good = saveBracket("合规支架", "A-01", 200, 100, source.getId());
        Bracket wrongModel = saveBracket("型号冲突", "B-99", 200, 100, source.getId());
        Bracket tooLong = saveBracket("超长支架", "A-01", 500, 100, source.getId());
        Bracket second = saveBracket("第二个合规", "A-01", 150, 80, source.getId());

        MvcResult result = mockMvc.perform(post("/api/binding/rehang-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(),
                                good.getId(), wrongModel.getId(), tooLong.getId(), second.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = dataOf(result);

        assertEquals(source.getId().longValue(), data.path("sourceEquipmentId").asLong());
        assertEquals("一号封口机", data.path("sourceEquipmentName").asText());
        assertEquals(target.getId().longValue(), data.path("equipmentId").asLong());
        assertEquals(0, data.path("currentCount").asInt());
        assertEquals(1, data.path("availableSlots").asInt());
        // 仅第一个合规支架通过：型号冲突、尺寸冲突各 1 个，容量只能容纳 1 个，第二个合规落入容量冲突
        assertEquals(1, data.path("passedItems").size());
        assertEquals(3, data.path("conflicts").size());
        assertEquals(good.getId().longValue(), data.path("passedItems").get(0).path("bracketId").asLong());

        String capacityReason = conflictReason(data, second.getId());
        assertTrue(capacityReason.contains("超出设备最大支架数量"), capacityReason);
        assertTrue(conflictReason(data, wrongModel.getId()).contains("型号不在允许范围内"));
        assertTrue(conflictReason(data, tooLong.getId()).contains("长度超出允许范围"));

        // 预检不落库：全部支架仍挂在源设备
        for (Bracket b : java.util.List.of(good, wrongModel, tooLong, second)) {
            assertEquals(source.getId(), bracketRepository.findById(b.getId()).orElseThrow().getEquipmentId());
        }
    }

    @Test
    void confirm_rehangsPassedInOneShot_andConflictsStayOnSource_withoutUnboundState() throws Exception {
        Equipment source = saveEquipment("SRC-002", "旧线封口机");
        Equipment target = saveEquipment("TGT-002", "新线封口机");
        target.setAllowedModels("A-01");
        equipmentRepository.save(target);

        Bracket good = saveBracket("可改挂", "A-01", 200, 100, source.getId());
        Bracket bad = saveBracket("改挂冲突", "B-99", 200, 100, source.getId());

        long unboundBefore = bracketRepository.countByEquipmentIdIsNull();

        MvcResult result = mockMvc.perform(post("/api/binding/rehang-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(), good.getId(), bad.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = dataOf(result);

        assertEquals(1, data.path("rehungCount").asInt());
        assertEquals(1, data.path("conflicts").size());

        // 通过项一次性改挂到目标设备，冲突项仍留在源设备
        assertEquals(target.getId(), bracketRepository.findById(good.getId()).orElseThrow().getEquipmentId());
        assertEquals(source.getId(), bracketRepository.findById(bad.getId()).orElseThrow().getEquipmentId());

        // 全程不经过未绑定：未绑定数量不变
        assertEquals(unboundBefore, bracketRepository.countByEquipmentIdIsNull());
        // 占用统计与档案一致：源设备剩 1、目标设备 1
        assertEquals(1, bracketRepository.findByEquipmentId(source.getId()).size());
        assertEquals(1, bracketRepository.findByEquipmentId(target.getId()).size());
    }

    @Test
    void unboundAndForeignBrackets_areConflicts_andSameEquipmentRejected() throws Exception {
        Equipment source = saveEquipment("SRC-003", "源机");
        Equipment other = saveEquipment("OTHER-003", "其他机");
        Equipment target = saveEquipment("TGT-003", "目标机");

        Bracket unbound = saveBracket("未绑定支架", "A-01", 100, 50, null);
        Bracket foreign = saveBracket("他机支架", "A-01", 100, 50, other.getId());

        MvcResult result = mockMvc.perform(post("/api/binding/rehang-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), target.getId(), unbound.getId(), foreign.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = dataOf(result);
        assertEquals(0, data.path("passedItems").size());
        assertEquals(2, data.path("conflicts").size());
        assertTrue(conflictReason(data, unbound.getId()).contains("未绑定，请使用批量绑定"));
        assertTrue(conflictReason(data, foreign.getId()).contains("未挂在源设备上"));

        // 源设备与目标设备相同：接口直接失败，且不产生任何改挂
        MvcResult sameResult = mockMvc.perform(post("/api/binding/rehang-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rehangJson(source.getId(), source.getId(), foreign.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sameBody = objectMapper.readTree(
                sameResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(sameBody.path("code").asText().equals("200"));
        assertEquals(other.getId(), bracketRepository.findById(foreign.getId()).orElseThrow().getEquipmentId());
    }

    private String conflictReason(JsonNode data, long bracketId) {
        for (JsonNode item : data.path("conflicts")) {
            if (item.path("bracketId").asLong() == bracketId) {
                return item.path("reason").asText();
            }
        }
        throw new IllegalStateException("未找到支架 " + bracketId + " 的冲突项");
    }
}
