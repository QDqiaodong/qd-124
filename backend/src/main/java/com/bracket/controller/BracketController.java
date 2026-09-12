package com.bracket.controller;

import com.bracket.dto.ApiResponse;
import com.bracket.dto.BracketCreateRequest;
import com.bracket.dto.PageResult;
import com.bracket.service.BracketService;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bracket")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BracketController {

    private final BracketService bracketService;

    @Autowired
    public BracketController(BracketService bracketService) {
        this.bracketService = bracketService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<BracketVO>> getPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer bindStatus,
            @RequestParam(required = false) String repairStatus) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiResponse.success(bracketService.findAll(name, model, bindStatus, repairStatus, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BracketVO> getById(@PathVariable Long id) {
        BracketVO bracket = bracketService.findById(id);
        if (bracket == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(bracket);
    }

    @PostMapping
    public ApiResponse<BracketVO> create(@RequestBody BracketCreateRequest request) {
        return ApiResponse.success(bracketService.save(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BracketVO> update(@PathVariable Long id, @RequestBody BracketCreateRequest request) {
        BracketVO updated = bracketService.update(id, request);
        if (updated == null) {
            return ApiResponse.fail("支架不存在");
        }
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bracketService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/models")
    public ApiResponse<List<String>> getPopularModels() {
        return ApiResponse.success(bracketService.getPopularModels());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getStats() {
        return ApiResponse.success(bracketService.getStats());
    }
}
