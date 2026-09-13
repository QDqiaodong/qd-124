package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.FirstArticleCreateRequest;
import com.bracket.dto.FirstArticleReleaseRequest;
import com.bracket.dto.FirstArticleReturnRequest;
import com.bracket.dto.PageResult;
import com.bracket.service.FirstArticleService;
import com.bracket.vo.FirstArticleInspectionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * 首件尺寸确认单：换模放量前开单确认首件尺寸，量差合格签放、超线退回再量。
 * 全部记录落库，列表按机台/放行结果筛选，详情可追查签字人与签字时间。
 */
@RestController
@RequestMapping("/api/first-articles")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FirstArticleController {

    private final FirstArticleService firstArticleService;

    @Autowired
    public FirstArticleController(FirstArticleService firstArticleService) {
        this.firstArticleService = firstArticleService;
    }

    /**
     * 开单：机台必须已登记当前模具批次；量差与超线标记开单时计算落库。
     */
    @PostMapping
    public ApiResponse<FirstArticleInspectionVO> create(@RequestBody FirstArticleCreateRequest request) {
        try {
            FirstArticleInspectionVO vo = firstArticleService.create(request);
            if (vo == null) {
                return ApiResponse.fail("设备不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 列表：按机台（equipmentId）与放行结果（status：PENDING/RELEASED/RETURNED）组合筛选。
     */
    @GetMapping
    public ApiResponse<PageResult<FirstArticleInspectionVO>> list(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
            return ApiResponse.success(firstArticleService.findPage(equipmentId, status, pageable));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 详情：点开查看签字人（签放人/退回人）与签字时间。
     */
    @GetMapping("/{id}")
    public ApiResponse<FirstArticleInspectionVO> detail(@PathVariable Long id) {
        FirstArticleInspectionVO vo = firstArticleService.findDetail(id);
        if (vo == null) {
            return ApiResponse.fail("确认单不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 签放：量差未超线的待签放单才可放行；超线单在此硬拦截，只能退回再量。
     */
    @PostMapping("/{id}/release")
    public ApiResponse<FirstArticleInspectionVO> release(@PathVariable Long id,
                                                         @RequestBody FirstArticleReleaseRequest request) {
        try {
            FirstArticleInspectionVO vo = firstArticleService.release(id, request);
            if (vo == null) {
                return ApiResponse.fail("确认单不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 退回再量：待签放单退回，重新测量需另开新单。
     */
    @PostMapping("/{id}/return")
    public ApiResponse<FirstArticleInspectionVO> returnForRemeasure(@PathVariable Long id,
                                                                    @RequestBody FirstArticleReturnRequest request) {
        try {
            FirstArticleInspectionVO vo = firstArticleService.returnForRemeasure(id, request);
            if (vo == null) {
                return ApiResponse.fail("确认单不存在");
            }
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
