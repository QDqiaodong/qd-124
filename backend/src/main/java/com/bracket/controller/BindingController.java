package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.BatchBindRequest;
import com.bracket.dto.BindConfirmRequest;
import com.bracket.service.BindingService;
import com.bracket.vo.BindCheckResultVO;
import com.bracket.vo.BindConfirmResultVO;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/binding")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BindingController {

    private final BindingService bindingService;

    @Autowired
    public BindingController(BindingService bindingService) {
        this.bindingService = bindingService;
    }

    /** 单个绑定前校验：返回通过项、冲突项及原因 */
    @PostMapping("/check")
    public ApiResponse<BindCheckResultVO> checkBind(@RequestBody Map<String, Long> request) {
        Long bracketId = request.get("bracketId");
        Long equipmentId = request.get("equipmentId");
        if (bracketId == null || equipmentId == null) {
            return ApiResponse.fail("支架ID和设备ID不能为空");
        }
        return ApiResponse.success(bindingService.checkBind(bracketId, equipmentId));
    }

    /** 批量绑定前校验：逐项返回通过/冲突结果 */
    @PostMapping("/batch-check")
    public ApiResponse<BindCheckResultVO> batchCheck(@RequestBody BatchBindRequest request) {
        if (request.getBracketIds() == null || request.getBracketIds().isEmpty()) {
            return ApiResponse.fail("请选择需要绑定的支架");
        }
        if (request.getEquipmentId() == null) {
            return ApiResponse.fail("请选择目标设备");
        }
        return ApiResponse.success(bindingService.checkBatchBind(request.getBracketIds(), request.getEquipmentId()));
    }

    /** 单个绑定确认：仅当校验通过时绑定 */
    @PostMapping("/confirm")
    public ApiResponse<BindConfirmResultVO> confirmBind(@RequestBody BindConfirmRequest request) {
        if (request.getBracketId() == null || request.getEquipmentId() == null) {
            return ApiResponse.fail("支架ID和设备ID不能为空");
        }
        BindCheckResultVO check = bindingService.checkBind(request.getBracketId(), request.getEquipmentId());
        if (!check.getConflicts().isEmpty()) {
            return ApiResponse.fail(check.getConflicts().get(0).getReason());
        }
        return ApiResponse.success(bindingService.confirmBind(request.getBracketId(), request.getEquipmentId()));
    }

    /** 批量绑定确认：仅绑定校验通过项，冲突项保持原样 */
    @PostMapping("/batch-confirm")
    public ApiResponse<BindConfirmResultVO> batchConfirm(@RequestBody BatchBindRequest request) {
        if (request.getBracketIds() == null || request.getBracketIds().isEmpty()) {
            return ApiResponse.fail("请选择需要绑定的支架");
        }
        if (request.getEquipmentId() == null) {
            return ApiResponse.fail("请选择目标设备");
        }
        return ApiResponse.success(bindingService.confirmBatchBind(request.getBracketIds(), request.getEquipmentId()));
    }

    @PostMapping("/unbind/{bracketId}")
    public ApiResponse<BracketVO> unbind(@PathVariable Long bracketId) {
        BracketVO result = bindingService.unbind(bracketId);
        if (result == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(result);
    }

    /**
     * 兼容旧接口：直接绑定同样强制校验配套规则，不通过则返回冲突原因。
     */
    @PostMapping("/bind")
    public ApiResponse<BindConfirmResultVO> bind(@RequestBody Map<String, Long> request) {
        Long bracketId = request.get("bracketId");
        Long equipmentId = request.get("equipmentId");
        if (bracketId == null || equipmentId == null) {
            return ApiResponse.fail("支架ID和设备ID不能为空");
        }
        BindCheckResultVO check = bindingService.checkBind(bracketId, equipmentId);
        if (!check.getConflicts().isEmpty()) {
            return ApiResponse.fail(check.getConflicts().get(0).getReason());
        }
        return ApiResponse.success(bindingService.confirmBind(bracketId, equipmentId));
    }

    /**
     * 兼容旧接口：批量绑定仅绑定校验通过项。
     */
    @PostMapping("/batch-bind")
    public ApiResponse<BindConfirmResultVO> batchBind(@RequestBody BatchBindRequest request) {
        List<Long> ids = request.getBracketIds() == null ? List.of() : request.getBracketIds();
        return ApiResponse.success(bindingService.confirmBatchBind(ids, request.getEquipmentId()));
    }
}
