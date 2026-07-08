package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.BatchBindRequest;
import com.bracket.service.BindingService;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/bind")
    public ApiResponse<BracketVO> bind(@RequestBody Map<String, Long> request) {
        Long bracketId = request.get("bracketId");
        Long equipmentId = request.get("equipmentId");
        BracketVO result = bindingService.bind(bracketId, equipmentId);
        if (result == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/unbind/{bracketId}")
    public ApiResponse<BracketVO> unbind(@PathVariable Long bracketId) {
        BracketVO result = bindingService.unbind(bracketId);
        if (result == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/batch-bind")
    public ApiResponse<Void> batchBind(@RequestBody BatchBindRequest request) {
        bindingService.batchBind(request.getBracketIds(), request.getEquipmentId());
        return ApiResponse.success();
    }
}
