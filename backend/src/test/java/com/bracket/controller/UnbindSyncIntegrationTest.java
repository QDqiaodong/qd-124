package com.bracket.controller;

import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 解绑后统计一致性端到端测试：
 * 在设备配套清单展开已挂支架并解绑后，设备占用（/equipment/list、/equipment/all 的 bracketCount
 * 与角标同源）、页顶未绑定个数（/equipment/unbound-count）、该机配套支架清单（/equipment/{id}/brackets）
 * 以及档案列表（/bracket/list、/bracket/stats）必须立即按库反映解绑后的新值，
 * 再次查询不能回弹到解绑前的旧占用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnbindSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private BracketRepository bracketRepository;

    @Autowired
    private MoldBatchRecordRepository moldBatchRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    @AfterEach
    void cleanUp() {
        bracketRepository.deleteAll();
        moldBatchRecordRepository.deleteAll();
        equipmentRepository.deleteAll();
    }

    @Test
    void unbind_updatesOccupancyAndUnboundCountImmediately_andStaysFreshOnRequery() throws Exception {
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode("FK-UB-1");
        equipment.setEquipmentName("解绑测试机");
        equipment.setMaxBrackets(3);
        equipment = equipmentRepository.save(equipment);

        Bracket first = new Bracket();
        first.setName("待解绑支架");
        first.setModel("ST-A001");
        first.setLengthMm(BigDecimal.valueOf(300));
        first.setWidthMm(BigDecimal.valueOf(150));
        first.setEquipmentId(equipment.getId());
        first = bracketRepository.save(first);

        Bracket second = new Bracket();
        second.setName("保留支架");
        second.setModel("ST-B002");
        second.setLengthMm(BigDecimal.valueOf(400));
        second.setWidthMm(BigDecimal.valueOf(200));
        second.setEquipmentId(equipment.getId());
        second = bracketRepository.save(second);

        // 解绑前：占用 2、未绑定 0
        assertEquals(2, bracketRepository.findByEquipmentId(equipment.getId()).size());
        assertEquals(0L, bracketRepository.countByEquipmentIdIsNull());

        // 执行解绑
        mockMvc.perform(post("/api/binding/unbind/" + first.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // 库里确实已解绑
        assertNull(bracketRepository.findById(first.getId()).orElseThrow().getEquipmentId());

        // 页顶未绑定个数立即 +1
        MvcResult countResult = mockMvc.perform(get("/api/equipment/unbound-count"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1L, dataOf(countResult).asLong());

        // 设备清单（分页）占用立即 -1，且重复查询不回弹
        for (int i = 0; i < 2; i++) {
            MvcResult listResult = mockMvc.perform(get("/api/equipment/list")
                            .param("pageNum", "1").param("pageSize", "6"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode list = dataOf(listResult).path("list");
            JsonNode eqNode = findEquipment(list, equipment.getId());
            assertEquals(1, eqNode.path("bracketCount").asInt(), "设备分页清单占用应为 1（第 " + (i + 1) + " 次查询）");
        }

        // 全量设备（批量绑定/改挂页下拉同源）占用也是 1
        MvcResult allResult = mockMvc.perform(get("/api/equipment/all"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode all = dataOf(allResult);
        assertEquals(1, findEquipment(all, equipment.getId()).path("bracketCount").asInt());

        // 该机配套支架清单只剩 1 项，解绑的支架不再出现
        MvcResult bracketsResult = mockMvc.perform(get("/api/equipment/" + equipment.getId() + "/brackets"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode brackets = dataOf(bracketsResult);
        assertEquals(1, brackets.size());
        assertEquals(second.getId(), brackets.get(0).path("id").asLong());

        // 档案统计：已绑定 1、未绑定 1；档案列表中该支架 equipmentId 为空
        MvcResult statsResult = mockMvc.perform(get("/api/bracket/stats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stats = dataOf(statsResult);
        assertEquals(2L, stats.path("total").asLong());
        assertEquals(1L, stats.path("bound").asLong());
        assertEquals(1L, stats.path("unbound").asLong());

        MvcResult archiveResult = mockMvc.perform(get("/api/bracket/list")
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode archiveList = dataOf(archiveResult).path("list");
        JsonNode unboundNode = findBracket(archiveList, first.getId());
        assertTrue(unboundNode.path("equipmentId").isNull() || unboundNode.path("equipmentId").isMissingNode());
        assertTrue(unboundNode.path("equipmentName").isNull() || unboundNode.path("equipmentName").asText().isEmpty());
    }

    private JsonNode findEquipment(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        throw new IllegalStateException("未找到设备 " + id);
    }

    private JsonNode findBracket(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        throw new IllegalStateException("未找到支架 " + id);
    }
}
