package com.bracket.service;

import com.bracket.dto.BracketRepairCreateRequest;
import com.bracket.dto.BracketRepairReturnRequest;
import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.BracketRepairRecord;
import com.bracket.repository.BracketRepairRecordRepository;
import com.bracket.repository.BracketRepository;
import com.bracket.vo.BracketRepairGateVO;
import com.bracket.vo.BracketRepairRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支架返修管理：送修登记、回库结论登记、按支架翻返修单、当前返修状态解析与
 * 批量挂接/换线改挂的返修放行闸门。
 *
 * 关键约束：
 * 1. 仅解绑（未绑定）后的支架可标记返修；一张返修单未回库时不能重复送修；
 * 2. 回库必须写下结论（合格/不合格）与检验人，缺一不可登记；
 * 3. 放行只认「当前返修单」（送修时间最新的一张）：未回库或结论不合格一律拦截
 *    批量挂接与换线改挂；合格才放行；旧返修单仅作历史追溯；
 * 4. 新返修单回库成功后，此前不合格的那条不再作为放行依据（也不再作为待处理项）。
 */
@Service
public class BracketRepairService {

    public static final String STATUS_REPAIRING = "REPAIRING";
    public static final String STATUS_RETURNED_QUALIFIED = "RETURNED_QUALIFIED";
    public static final String STATUS_RETURNED_UNQUALIFIED = "RETURNED_UNQUALIFIED";

    private final BracketRepairRecordRepository repairRecordRepository;
    private final BracketRepository bracketRepository;

    @Autowired
    public BracketRepairService(BracketRepairRecordRepository repairRecordRepository,
                                BracketRepository bracketRepository) {
        this.repairRecordRepository = repairRecordRepository;
        this.bracketRepository = bracketRepository;
    }

    /**
     * 送修登记：为已解绑的支架创建返修单（返修中，尚未回库）。
     */
    @Transactional
    public BracketRepairRecordVO createRepair(Long bracketId, BracketRepairCreateRequest request) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        if (bracket.getEquipmentId() != null) {
            throw new IllegalArgumentException("支架仍挂在设备上，请先解绑再标记返修");
        }
        if (request == null) {
            throw new IllegalArgumentException("送修参数不能为空");
        }
        String repairNo = trim(request.getRepairNo());
        if (repairNo == null) {
            throw new IllegalArgumentException("返修单号不能为空");
        }
        BracketRepairRecord current = findCurrentRecord(bracketId);
        if (current != null && current.getReturnResult() == null) {
            throw new IllegalArgumentException("返修单 " + current.getRepairNo()
                    + " 尚未写回库结论与检验人，不能重复送修；请先完成回库登记");
        }

        BracketRepairRecord record = new BracketRepairRecord();
        record.setBracketId(bracketId);
        record.setRepairNo(repairNo);
        record.setRepairReason(trim(request.getRepairReason()));
        record.setRepairTime(request.getRepairTime() != null ? request.getRepairTime() : LocalDateTime.now());
        record.setRepairOperator(trim(request.getRepairOperator()));
        BracketRepairRecord saved = repairRecordRepository.save(record);
        return toVO(saved, bracket, true);
    }

    /**
     * 回库登记：为返修单写下回库结论与检验人。结论与检验人必填；已回库的返修单不可改写。
     */
    @Transactional
    public BracketRepairRecordVO returnToStore(Long bracketId, Long recordId, BracketRepairReturnRequest request) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        BracketRepairRecord record = repairRecordRepository.findById(recordId).orElse(null);
        if (record == null || !bracketId.equals(record.getBracketId())) {
            throw new IllegalArgumentException("返修单不存在或不属于该支架");
        }
        if (request == null) {
            throw new IllegalArgumentException("回库参数不能为空");
        }
        if (record.getReturnResult() != null) {
            throw new IllegalArgumentException("返修单 " + record.getRepairNo() + " 已写回库结论，不能重复登记");
        }
        if (request.getReturnResult() == null) {
            throw new IllegalArgumentException("回库结论（合格/不合格）不能为空");
        }
        String inspector = trim(request.getInspector());
        if (inspector == null) {
            throw new IllegalArgumentException("检验人不能为空：返修支架回库必须写下检验人才能再次挂接");
        }

        record.setReturnResult(request.getReturnResult());
        record.setInspector(inspector);
        record.setReturnTime(request.getReturnTime() != null ? request.getReturnTime() : LocalDateTime.now());
        record.setReturnRemark(trim(request.getReturnRemark()));
        BracketRepairRecord saved = repairRecordRepository.save(record);
        BracketRepairRecord current = findCurrentRecord(bracketId);
        return toVO(saved, bracket, current != null && saved.getId().equals(current.getId()));
    }

    /** 按支架翻返修单，送修时间新的在前；第一条为当前返修单。 */
    public PageResult<BracketRepairRecordVO> findHistory(Long bracketId, Pageable pageable) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        Page<BracketRepairRecord> page = repairRecordRepository
                .findByBracketIdOrderByRepairTimeDescIdDesc(bracketId, pageable);
        BracketRepairRecord current = findCurrentRecord(bracketId);
        Long currentId = current == null ? null : current.getId();
        List<BracketRepairRecordVO> list = page.getContent().stream()
                .map(r -> toVO(r, bracket, r.getId().equals(currentId)))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 当前返修单（最新一张）；从未返修时返回 null。 */
    public BracketRepairRecord findCurrentRecord(Long bracketId) {
        if (bracketId == null) {
            return null;
        }
        return repairRecordRepository
                .findFirstByBracketIdOrderByRepairTimeDescIdDesc(bracketId)
                .orElse(null);
    }

    /**
     * 批量解析多个支架的当前返修单：key=支架ID，value=最新返修单（无返修记录的支架不出现）。
     */
    public Map<Long, BracketRepairRecord> findCurrentRecordMap(List<Long> bracketIds) {
        Map<Long, BracketRepairRecord> map = new HashMap<>();
        if (bracketIds == null || bracketIds.isEmpty()) {
            return map;
        }
        for (Long id : bracketIds.stream().distinct().collect(Collectors.toList())) {
            BracketRepairRecord current = findCurrentRecord(id);
            if (current != null) {
                map.put(id, current);
            }
        }
        return map;
    }

    /**
     * 返修放行闸门：按支架当前（最新）返修单评估能否批量挂接/改挂。
     * 从未返修返回 null（不经过返修闸门）；返修中或回库不合格返回 passed=false 并写明原因。
     */
    public BracketRepairGateVO evaluateGate(Bracket bracket) {
        if (bracket == null) {
            return null;
        }
        BracketRepairRecord current = findCurrentRecord(bracket.getId());
        if (current == null) {
            return null;
        }
        return toGate(bracket, current);
    }

    /** 与 {@link #evaluateGate(Bracket)} 同一口径，直接基于已查出的当前返修单构造。 */
    public BracketRepairGateVO toGate(Bracket bracket, BracketRepairRecord current) {
        String label = "支架「" + bracket.getName() + "」";
        if (current.getReturnResult() == null) {
            return new BracketRepairGateVO(false,
                    label + "已标记返修（返修单 " + current.getRepairNo()
                            + "），尚未写回库结论与检验人，不能批量挂接/改挂，请先完成回库检验登记",
                    current.getId(), current.getRepairNo(), STATUS_REPAIRING, null,
                    null, null);
        }
        if (Boolean.FALSE.equals(current.getReturnResult())) {
            return new BracketRepairGateVO(false,
                    label + "返修单 " + current.getRepairNo() + " 回库结论为不合格"
                            + (current.getInspector() != null ? "（检验人：" + current.getInspector() + "）" : "")
                            + "，禁止批量挂接/改挂；如需重新上线请再次送修并检验合格回库",
                    current.getId(), current.getRepairNo(), STATUS_RETURNED_UNQUALIFIED, false,
                    current.getInspector(), current.getReturnTime());
        }
        return new BracketRepairGateVO(true, null, current.getId(), current.getRepairNo(),
                STATUS_RETURNED_QUALIFIED, true, current.getInspector(), current.getReturnTime());
    }

    public static String resolveStatus(BracketRepairRecord record) {
        if (record == null || record.getReturnResult() == null) {
            return STATUS_REPAIRING;
        }
        return Boolean.TRUE.equals(record.getReturnResult())
                ? STATUS_RETURNED_QUALIFIED
                : STATUS_RETURNED_UNQUALIFIED;
    }

    private BracketRepairRecordVO toVO(BracketRepairRecord record, Bracket bracket, boolean current) {
        BracketRepairRecordVO vo = new BracketRepairRecordVO();
        vo.setId(record.getId());
        vo.setBracketId(record.getBracketId());
        vo.setBracketName(bracket.getName());
        vo.setBracketModel(bracket.getModel());
        vo.setRepairNo(record.getRepairNo());
        vo.setRepairReason(record.getRepairReason());
        vo.setRepairTime(record.getRepairTime());
        vo.setRepairOperator(record.getRepairOperator());
        vo.setReturnResult(record.getReturnResult());
        vo.setReturnTime(record.getReturnTime());
        vo.setInspector(record.getInspector());
        vo.setReturnRemark(record.getReturnRemark());
        vo.setCreateTime(record.getCreateTime());
        vo.setCurrent(current);
        vo.setStatus(resolveStatus(record));
        return vo;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
