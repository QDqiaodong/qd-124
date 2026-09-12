package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.BracketRepairCreateRequest;
import com.bracket.dto.BracketRepairReturnRequest;
import com.bracket.dto.PageResult;
import com.bracket.service.BracketRepairService;
import com.bracket.vo.BracketRepairRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * 支架返修管理：解绑后的支架送修登记、回库结论登记、按支架翻返修单。
 */
@RestController
@RequestMapping("/api/bracket/{bracketId}/repairs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BracketRepairController {

    private final BracketRepairService bracketRepairService;

    @Autowired
    public BracketRepairController(BracketRepairService bracketRepairService) {
        this.bracketRepairService = bracketRepairService;
    }

    /**
     * 送修登记：把已解绑支架标记为返修，生成返修单（返修中，尚未回库）。
     * 已绑定/已有未回库返修单时拒绝写入。
     */
    @PostMapping
    public ApiResponse<BracketRepairRecordVO> create(@PathVariable Long bracketId,
                                                     @RequestBody BracketRepairCreateRequest request) {
        try {
            BracketRepairRecordVO vo = bracketRepairService.createRepair(bracketId, request);
            if (vo == null) {
                return ApiResponse.fail("支架不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 回库登记：为返修单写下回库结论与检验人（二者必填）。合格后该支架才可再次批量挂接/改挂。
     */
    @PutMapping("/{recordId}/return")
    public ApiResponse<BracketRepairRecordVO> returnToStore(@PathVariable Long bracketId,
                                                            @PathVariable Long recordId,
                                                            @RequestBody BracketRepairReturnRequest request) {
        try {
            BracketRepairRecordVO vo = bracketRepairService.returnToStore(bracketId, recordId, request);
            if (vo == null) {
                return ApiResponse.fail("支架不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 按支架翻返修单：送修时间新的在前，第一条为当前返修单（唯一放行依据）。
     */
    @GetMapping
    public ApiResponse<PageResult<BracketRepairRecordVO>> history(
            @PathVariable Long bracketId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        PageResult<BracketRepairRecordVO> page = bracketRepairService.findHistory(bracketId, pageable);
        if (page == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(page);
    }
}
