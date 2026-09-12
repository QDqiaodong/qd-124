package com.bracket.service;

import com.bracket.dto.MoldBatchRegisterRequest;
import com.bracket.dto.PageResult;
import com.bracket.entity.Equipment;
import com.bracket.entity.MoldBatchRecord;
import com.bracket.repository.EquipmentRepository;
import com.bracket.repository.MoldBatchRecordRepository;
import com.bracket.vo.MoldBatchGateVO;
import com.bracket.vo.MoldBatchRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 换模批次管理：批次登记、按设备翻历史、当前批次解析与批量挂接/换线放行闸门。
 *
 * 关键约束：
 * 1. 换模后必须为封口机写下当前模具批次，且批次模具型号必须在该机允许清单内，登记才成功；
 * 2. 放行只认「当前批次」（换模时间最新的一条），换新批次后旧记录保留作历史但永不再放行；
 * 3. 未写批次或当前批次型号不在允许清单内时，批量挂接与换线改挂一律整单拦截。
 */
@Service
public class MoldBatchService {

    private final MoldBatchRecordRepository moldBatchRecordRepository;
    private final EquipmentRepository equipmentRepository;

    @Autowired
    public MoldBatchService(MoldBatchRecordRepository moldBatchRecordRepository,
                            EquipmentRepository equipmentRepository) {
        this.moldBatchRecordRepository = moldBatchRecordRepository;
        this.equipmentRepository = equipmentRepository;
    }

    /**
     * 登记当前模具批次（换模后写批次）。
     * 不覆盖旧记录：每次登记都新插入一条历史，最新一条即当前批次。
     */
    @Transactional
    public MoldBatchRecordVO register(Long equipmentId, MoldBatchRegisterRequest request) {
        Equipment equipment = equipmentRepository.findById(equipmentId).orElse(null);
        if (equipment == null) {
            return null;
        }
        if (request == null) {
            throw new IllegalArgumentException("登记参数不能为空");
        }
        String batchNo = request.getBatchNo() == null ? null : request.getBatchNo().trim();
        String moldModel = request.getMoldModel() == null ? null : request.getMoldModel().trim();
        if (batchNo == null || batchNo.isEmpty()) {
            throw new IllegalArgumentException("模具批次号不能为空，换模后必须先登记当前批次");
        }
        if (moldModel == null || moldModel.isEmpty()) {
            throw new IllegalArgumentException("模具型号不能为空");
        }
        List<String> allowed = parseMoldModels(equipment.getAllowedMoldModels());
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("该机尚未维护允许模具型号清单，无法登记批次，请先在配套规则中配置");
        }
        if (!allowed.contains(moldModel)) {
            throw new IllegalArgumentException("模具型号 " + moldModel + " 不在该机允许清单内（允许："
                    + String.join("、", allowed) + "），不能登记为当前批次");
        }

        MoldBatchRecord record = new MoldBatchRecord();
        record.setEquipmentId(equipmentId);
        record.setBatchNo(batchNo);
        record.setMoldModel(moldModel);
        record.setChangeTime(request.getChangeTime() != null ? request.getChangeTime() : LocalDateTime.now());
        record.setOperator(trimToNull(request.getOperator()));
        record.setRemark(trimToNull(request.getRemark()));
        MoldBatchRecord saved = moldBatchRecordRepository.save(record);
        return toVO(saved, equipment, true);
    }

    /** 按设备翻历史换模记录，最新（当前批次）在前。 */
    public PageResult<MoldBatchRecordVO> findHistory(Long equipmentId, Pageable pageable) {
        Equipment equipment = equipmentRepository.findById(equipmentId).orElse(null);
        if (equipment == null) {
            return null;
        }
        Page<MoldBatchRecord> page = moldBatchRecordRepository
                .findByEquipmentIdOrderByChangeTimeDescIdDesc(equipmentId, pageable);
        MoldBatchRecord current = findCurrentRecord(equipmentId);
        Long currentId = current == null ? null : current.getId();
        List<MoldBatchRecordVO> list = page.getContent().stream()
                .map(r -> toVO(r, equipment, r.getId().equals(currentId)))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 当前批次（最新一条）；从未登记时返回 null。 */
    public MoldBatchRecord findCurrentRecord(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return moldBatchRecordRepository
                .findFirstByEquipmentIdOrderByChangeTimeDescIdDesc(equipmentId)
                .orElse(null);
    }

    /**
     * 放行闸门：评估目标设备当前模具批次能否放行批量挂接/换线改挂。
     * 仅最新一条记录可作为放行依据。
     */
    public MoldBatchGateVO evaluateGate(Equipment equipment) {
        if (equipment == null) {
            return new MoldBatchGateVO(false, "目标设备不存在", null, null, null);
        }
        MoldBatchRecord current = findCurrentRecord(equipment.getId());
        if (current == null) {
            return new MoldBatchGateVO(false,
                    "设备 " + equipment.getEquipmentCode() + " 换模后尚未登记当前模具批次，请先登记再执行挂接/换线",
                    null, null, null);
        }
        List<String> allowed = parseMoldModels(equipment.getAllowedMoldModels());
        if (allowed.isEmpty() || !allowed.contains(current.getMoldModel())) {
            String allowedText = allowed.isEmpty() ? "未配置允许清单" : "允许：" + String.join("、", allowed);
            return new MoldBatchGateVO(false,
                    "设备 " + equipment.getEquipmentCode() + " 当前模具批次（" + current.getBatchNo()
                            + "，型号 " + current.getMoldModel() + "）不在该机允许清单内（" + allowedText
                            + "），请重新登记允许型号的当前批次",
                    current.getId(), current.getBatchNo(), current.getMoldModel());
        }
        return new MoldBatchGateVO(true, null, current.getId(), current.getBatchNo(), current.getMoldModel());
    }

    private MoldBatchRecordVO toVO(MoldBatchRecord record, Equipment equipment, boolean current) {
        MoldBatchRecordVO vo = new MoldBatchRecordVO();
        vo.setId(record.getId());
        vo.setEquipmentId(record.getEquipmentId());
        vo.setEquipmentCode(equipment.getEquipmentCode());
        vo.setEquipmentName(equipment.getEquipmentName());
        vo.setBatchNo(record.getBatchNo());
        vo.setMoldModel(record.getMoldModel());
        vo.setChangeTime(record.getChangeTime());
        vo.setOperator(record.getOperator());
        vo.setRemark(record.getRemark());
        vo.setCreateTime(record.getCreateTime());
        vo.setCurrent(current);
        return vo;
    }

    /** 规则配置中按逗号分隔的允许模具型号清单。 */
    public static List<String> parseMoldModels(String allowedMoldModels) {
        if (allowedMoldModels == null || allowedMoldModels.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedMoldModels.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public static String normalizeMoldModels(String allowedMoldModels) {
        List<String> models = parseMoldModels(allowedMoldModels);
        return models.isEmpty() ? null : String.join(",", models);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
