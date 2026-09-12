package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.MoldBatchRegisterRequest;
import com.bracket.dto.PageResult;
import com.bracket.service.MoldBatchService;
import com.bracket.vo.MoldBatchRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 换模批次管理：换模后为封口机登记当前模具批次、按设备翻历史换模记录。
 */
@RestController
@RequestMapping("/api/equipment/{equipmentId}/mold-batches")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MoldBatchController {

    private final MoldBatchService moldBatchService;

    @Autowired
    public MoldBatchController(MoldBatchService moldBatchService) {
        this.moldBatchService = moldBatchService;
    }

    /**
     * 换模后登记当前模具批次：批次型号必须在该机允许清单内，否则拒绝写入。
     * 每次登记新增一条历史，最新一条即当前批次（旧批次不再作为放行依据）。
     */
    @PostMapping
    public ApiResponse<MoldBatchRecordVO> register(@PathVariable Long equipmentId,
                                                   @RequestBody MoldBatchRegisterRequest request) {
        try {
            MoldBatchRecordVO vo = moldBatchService.register(equipmentId, request);
            if (vo == null) {
                return ApiResponse.fail("设备不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 按设备翻历史换模记录：换模时间新的在前，第一条为当前批次。
     */
    @GetMapping
    public ApiResponse<PageResult<MoldBatchRecordVO>> history(
            @PathVariable Long equipmentId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "changeTime").and(Sort.by(Sort.Direction.DESC, "id")));
        PageResult<MoldBatchRecordVO> page = moldBatchService.findHistory(equipmentId, pageable);
        if (page == null) {
            return ApiResponse.fail("设备不存在");
        }
        return ApiResponse.success(page);
    }
}
