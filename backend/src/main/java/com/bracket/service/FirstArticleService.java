package com.bracket.service;

import com.bracket.dto.FirstArticleCreateRequest;
import com.bracket.dto.FirstArticleReleaseRequest;
import com.bracket.dto.FirstArticleReturnRequest;
import com.bracket.dto.PageResult;
import com.bracket.entity.Equipment;
import com.bracket.entity.FirstArticleInspection;
import com.bracket.entity.MoldBatchRecord;
import com.bracket.repository.EquipmentRepository;
import com.bracket.repository.FirstArticleInspectionRepository;
import com.bracket.vo.FirstArticleInspectionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首件尺寸确认单：换模放量前的首件尺寸确认、签放与退回再量。
 *
 * 关键约束：
 * 1. 开单只认机台「当前模具批次」（最新一条换模记录），未登记批次不能开单；
 *    批次号/型号快照落库，后续换批次不影响历史单追溯；
 * 2. 量差 = 实测 - 标准，任一维度 |量差| > 公差 即超线；超线单永远不能放行量产，
 *    只能退回再量（退回为终态，重新测量需另开新单）；
 * 3. 签放/退回都必须写签字人与签字时间；全部记录落库，关掉页面再进记录仍在。
 */
@Service
public class FirstArticleService {

    private static final DateTimeFormatter FORM_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FirstArticleInspectionRepository inspectionRepository;
    private final EquipmentRepository equipmentRepository;
    private final MoldBatchService moldBatchService;

    @Autowired
    public FirstArticleService(FirstArticleInspectionRepository inspectionRepository,
                               EquipmentRepository equipmentRepository,
                               MoldBatchService moldBatchService) {
        this.inspectionRepository = inspectionRepository;
        this.equipmentRepository = equipmentRepository;
        this.moldBatchService = moldBatchService;
    }

    /**
     * 开单：为机台当前模具批次登记首件尺寸确认单。
     * 量差与超线标记在开单时计算落库，此后不可改；超线单只能退回再量。
     */
    @Transactional
    public FirstArticleInspectionVO create(FirstArticleCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("开单参数不能为空");
        }
        if (request.getEquipmentId() == null) {
            throw new IllegalArgumentException("请选择机台");
        }
        Equipment equipment = equipmentRepository.findById(request.getEquipmentId()).orElse(null);
        if (equipment == null) {
            return null;
        }
        String operator = trimToNull(request.getOperator());
        if (operator == null) {
            throw new IllegalArgumentException("开单人不能为空，请填写调度姓名");
        }
        requirePositive(request.getStandardLength(), "标准长");
        requirePositive(request.getStandardWidth(), "标准宽");
        requirePositive(request.getStandardHeight(), "标准高");
        requirePositive(request.getMeasuredLength(), "实测长");
        requirePositive(request.getMeasuredWidth(), "实测宽");
        requirePositive(request.getMeasuredHeight(), "实测高");
        requirePositive(request.getTolerance(), "公差");

        MoldBatchRecord currentBatch = moldBatchService.findCurrentRecord(equipment.getId());
        if (currentBatch == null) {
            throw new IllegalArgumentException("设备 " + equipment.getEquipmentCode()
                    + " 换模后尚未登记当前模具批次，不能开首件确认单，请先在设备配套清单完成换模登记");
        }

        FirstArticleInspection inspection = new FirstArticleInspection();
        inspection.setEquipmentId(equipment.getId());
        inspection.setMoldBatchRecordId(currentBatch.getId());
        inspection.setBatchNo(currentBatch.getBatchNo());
        inspection.setMoldModel(currentBatch.getMoldModel());
        inspection.setStandardLength(scale(request.getStandardLength()));
        inspection.setStandardWidth(scale(request.getStandardWidth()));
        inspection.setStandardHeight(scale(request.getStandardHeight()));
        inspection.setTolerance(scale(request.getTolerance()));
        inspection.setMeasuredLength(scale(request.getMeasuredLength()));
        inspection.setMeasuredWidth(scale(request.getMeasuredWidth()));
        inspection.setMeasuredHeight(scale(request.getMeasuredHeight()));

        BigDecimal lengthDeviation = deviation(inspection.getMeasuredLength(), inspection.getStandardLength());
        BigDecimal widthDeviation = deviation(inspection.getMeasuredWidth(), inspection.getStandardWidth());
        BigDecimal heightDeviation = deviation(inspection.getMeasuredHeight(), inspection.getStandardHeight());
        inspection.setLengthDeviation(lengthDeviation);
        inspection.setWidthDeviation(widthDeviation);
        inspection.setHeightDeviation(heightDeviation);
        inspection.setOutOfTolerance(isOver(lengthDeviation, inspection.getTolerance())
                || isOver(widthDeviation, inspection.getTolerance())
                || isOver(heightDeviation, inspection.getTolerance()));

        inspection.setStatus(FirstArticleInspection.STATUS_PENDING);
        inspection.setOperator(operator);
        inspection.setRemark(trimToNull(request.getRemark()));

        FirstArticleInspection saved = inspectionRepository.save(inspection);
        // 单号依赖自增 ID，先落库拿到 ID 再回填（同事务，对外一次生效）
        saved.setFormNo("FA-" + saved.getCreateTime().format(FORM_NO_DATE) + "-"
                + String.format("%04d", saved.getId()));
        saved = inspectionRepository.save(saved);
        return toVO(saved, equipment);
    }

    /**
     * 签放：仅待签放且量差未超线的单可放行；超线单在此被硬拦截，只能退回再量。
     */
    @Transactional
    public FirstArticleInspectionVO release(Long id, FirstArticleReleaseRequest request) {
        FirstArticleInspection inspection = inspectionRepository.findById(id).orElse(null);
        if (inspection == null) {
            return null;
        }
        if (request == null || trimToNull(request.getSigner()) == null) {
            throw new IllegalArgumentException("签放人不能为空，请填写签字人姓名");
        }
        if (!FirstArticleInspection.STATUS_PENDING.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("该单已" + statusText(inspection.getStatus()) + "，不能重复签放");
        }
        if (inspection.isOutOfTolerance()) {
            throw new IllegalArgumentException("量差超线（" + overLineSummary(inspection)
                    + "），不能放行量产，只能退回再量");
        }
        inspection.setStatus(FirstArticleInspection.STATUS_RELEASED);
        inspection.setReleaseSigner(trimToNull(request.getSigner()));
        inspection.setReleaseTime(request.getReleaseTime() != null ? request.getReleaseTime() : LocalDateTime.now());
        return toVO(inspectionRepository.save(inspection), loadEquipment(inspection.getEquipmentId()));
    }

    /**
     * 退回再量：仅待签放的单可退回；退回为终态，重新测量需另开新单。
     */
    @Transactional
    public FirstArticleInspectionVO returnForRemeasure(Long id, FirstArticleReturnRequest request) {
        FirstArticleInspection inspection = inspectionRepository.findById(id).orElse(null);
        if (inspection == null) {
            return null;
        }
        if (request == null || trimToNull(request.getOperator()) == null) {
            throw new IllegalArgumentException("退回人不能为空，请填写操作人姓名");
        }
        if (!FirstArticleInspection.STATUS_PENDING.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("该单已" + statusText(inspection.getStatus()) + "，不能再退回");
        }
        inspection.setStatus(FirstArticleInspection.STATUS_RETURNED);
        inspection.setReturnOperator(trimToNull(request.getOperator()));
        inspection.setReturnReason(trimToNull(request.getReason()));
        inspection.setReturnTime(request.getReturnTime() != null ? request.getReturnTime() : LocalDateTime.now());
        return toVO(inspectionRepository.save(inspection), loadEquipment(inspection.getEquipmentId()));
    }

    /** 列表：可按机台、放行结果组合筛选，开单时间新的在前。 */
    public PageResult<FirstArticleInspectionVO> findPage(Long equipmentId, String status, Pageable pageable) {
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null && !FirstArticleInspection.STATUS_PENDING.equals(normalizedStatus)
                && !FirstArticleInspection.STATUS_RELEASED.equals(normalizedStatus)
                && !FirstArticleInspection.STATUS_RETURNED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("放行结果只能是 PENDING（待签放）/ RELEASED（已放行）/ RETURNED（已退回再量）");
        }
        Page<FirstArticleInspection> page;
        if (equipmentId != null && normalizedStatus != null) {
            page = inspectionRepository.findByEquipmentIdAndStatusOrderByCreateTimeDescIdDesc(
                    equipmentId, normalizedStatus, pageable);
        } else if (equipmentId != null) {
            page = inspectionRepository.findByEquipmentIdOrderByCreateTimeDescIdDesc(equipmentId, pageable);
        } else if (normalizedStatus != null) {
            page = inspectionRepository.findByStatusOrderByCreateTimeDescIdDesc(normalizedStatus, pageable);
        } else {
            page = inspectionRepository.findAllByOrderByCreateTimeDescIdDesc(pageable);
        }
        Set<Long> equipmentIds = page.getContent().stream()
                .map(FirstArticleInspection::getEquipmentId)
                .collect(Collectors.toSet());
        Map<Long, Equipment> equipmentMap = equipmentRepository.findAllById(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        List<FirstArticleInspectionVO> list = page.getContent().stream()
                .map(r -> toVO(r, equipmentMap.get(r.getEquipmentId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 详情：点开可看签字人与签字时间。 */
    public FirstArticleInspectionVO findDetail(Long id) {
        FirstArticleInspection inspection = inspectionRepository.findById(id).orElse(null);
        if (inspection == null) {
            return null;
        }
        return toVO(inspection, loadEquipment(inspection.getEquipmentId()));
    }

    private Equipment loadEquipment(Long equipmentId) {
        return equipmentRepository.findById(equipmentId).orElse(null);
    }

    /** 量差 = 实测 - 标准（带符号，保留两位小数） */
    private BigDecimal deviation(BigDecimal measured, BigDecimal standard) {
        return measured.subtract(standard).setScale(2, RoundingMode.HALF_UP);
    }

    /** 是否超线：|量差| > 公差（恰好在公差线上不算超线） */
    private boolean isOver(BigDecimal deviation, BigDecimal tolerance) {
        return deviation.abs().compareTo(tolerance) > 0;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
    }

    /** 超线摘要：列出超线维度与量差，用于拦截提示 */
    private String overLineSummary(FirstArticleInspection inspection) {
        StringBuilder sb = new StringBuilder();
        appendOver(sb, "长", inspection.getLengthDeviation(), inspection.getTolerance());
        appendOver(sb, "宽", inspection.getWidthDeviation(), inspection.getTolerance());
        appendOver(sb, "高", inspection.getHeightDeviation(), inspection.getTolerance());
        return sb.length() == 0 ? "量差超线" : sb.toString();
    }

    private void appendOver(StringBuilder sb, String label, BigDecimal deviation, BigDecimal tolerance) {
        if (isOver(deviation, tolerance)) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(label).append("量差 ").append(deviation.stripTrailingZeros().toPlainString())
                    .append("mm，超出公差 ±").append(tolerance.stripTrailingZeros().toPlainString()).append("mm");
        }
    }

    private String statusText(String status) {
        if (FirstArticleInspection.STATUS_RELEASED.equals(status)) {
            return "放行";
        }
        if (FirstArticleInspection.STATUS_RETURNED.equals(status)) {
            return "退回再量";
        }
        return "待签放";
    }

    private FirstArticleInspectionVO toVO(FirstArticleInspection inspection, Equipment equipment) {
        FirstArticleInspectionVO vo = new FirstArticleInspectionVO();
        vo.setId(inspection.getId());
        vo.setFormNo(inspection.getFormNo());
        vo.setEquipmentId(inspection.getEquipmentId());
        if (equipment != null) {
            vo.setEquipmentCode(equipment.getEquipmentCode());
            vo.setEquipmentName(equipment.getEquipmentName());
        }
        vo.setMoldBatchRecordId(inspection.getMoldBatchRecordId());
        vo.setBatchNo(inspection.getBatchNo());
        vo.setMoldModel(inspection.getMoldModel());
        vo.setStandardLength(inspection.getStandardLength());
        vo.setStandardWidth(inspection.getStandardWidth());
        vo.setStandardHeight(inspection.getStandardHeight());
        vo.setTolerance(inspection.getTolerance());
        vo.setMeasuredLength(inspection.getMeasuredLength());
        vo.setMeasuredWidth(inspection.getMeasuredWidth());
        vo.setMeasuredHeight(inspection.getMeasuredHeight());
        vo.setLengthDeviation(inspection.getLengthDeviation());
        vo.setWidthDeviation(inspection.getWidthDeviation());
        vo.setHeightDeviation(inspection.getHeightDeviation());
        vo.setOutOfTolerance(inspection.isOutOfTolerance());
        vo.setStatus(inspection.getStatus());
        vo.setOperator(inspection.getOperator());
        vo.setRemark(inspection.getRemark());
        vo.setReleaseSigner(inspection.getReleaseSigner());
        vo.setReleaseTime(inspection.getReleaseTime());
        vo.setReturnOperator(inspection.getReturnOperator());
        vo.setReturnReason(inspection.getReturnReason());
        vo.setReturnTime(inspection.getReturnTime());
        vo.setCreateTime(inspection.getCreateTime());
        return vo;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
