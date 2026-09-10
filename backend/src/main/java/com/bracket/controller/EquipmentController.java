package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.EquipmentRuleRequest;
import com.bracket.dto.PageResult;
import com.bracket.service.EquipmentService;
import com.bracket.vo.BracketVO;
import com.bracket.vo.EquipmentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EquipmentController {

    private final EquipmentService equipmentService;

    @Autowired
    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<EquipmentVO>> getPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiResponse.success(equipmentService.findAll(code, name, pageable));
    }

    @GetMapping("/all")
    public ApiResponse<List<EquipmentVO>> getAll() {
        return ApiResponse.success(equipmentService.findAllWithBracketCount());
    }

    @GetMapping("/{id}/brackets")
    public ApiResponse<List<BracketVO>> getBrackets(@PathVariable Long id) {
        return ApiResponse.success(equipmentService.findBracketsByEquipmentId(id));
    }

    @GetMapping("/unbound-count")
    public ApiResponse<Long> getUnboundCount() {
        return ApiResponse.success(equipmentService.getUnboundCount());
    }

    /** 维护设备配套规则：最大支架数量、允许型号、长宽范围；修改不影响已有绑定 */
    @PutMapping("/{id}/rule")
    public ApiResponse<EquipmentVO> updateRule(@PathVariable Long id, @RequestBody EquipmentRuleRequest request) {
        try {
            EquipmentVO vo = equipmentService.updateRule(id, request);
            if (vo == null) {
                return ApiResponse.fail("设备不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
