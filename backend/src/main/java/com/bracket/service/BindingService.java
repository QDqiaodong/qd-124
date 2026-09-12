package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.entity.BracketRepairRecord;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BindCheckItemVO;
import com.bracket.vo.BindCheckResultVO;
import com.bracket.vo.BindConfirmResultVO;
import com.bracket.vo.BracketRepairGateVO;
import com.bracket.vo.BracketVO;
import com.bracket.vo.MoldBatchGateVO;
import com.bracket.vo.RehangCheckResultVO;
import com.bracket.vo.RehangConfirmResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BindingService {

    private final BracketRepository bracketRepository;
    private final EquipmentRepository equipmentRepository;
    private final BracketService bracketService;
    private final RuleMatcher ruleMatcher;
    private final MoldBatchService moldBatchService;
    private final BracketRepairService bracketRepairService;

    @Autowired
    public BindingService(BracketRepository bracketRepository, EquipmentRepository equipmentRepository,
                          BracketService bracketService, RuleMatcher ruleMatcher,
                          MoldBatchService moldBatchService,
                          BracketRepairService bracketRepairService) {
        this.bracketRepository = bracketRepository;
        this.equipmentRepository = equipmentRepository;
        this.bracketService = bracketService;
        this.ruleMatcher = ruleMatcher;
        this.moldBatchService = moldBatchService;
        this.bracketRepairService = bracketRepairService;
    }

    @Transactional
    public BracketVO unbind(Long bracketId) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        bracket.setEquipmentId(null);
        Bracket saved = bracketRepository.save(bracket);
        return bracketService.convertToVO(saved);
    }

    /**
     * 单个绑定前的配套规则校验。单个绑定不走换模批次闸门与返修闸门（批量挂接与换线改挂才强制）。
     */
    public BindCheckResultVO checkBind(Long bracketId, Long equipmentId) {
        return doCheck(List.of(bracketId), equipmentId, false);
    }

    /**
     * 批量绑定前的配套规则校验。换模批次未就绪（未写当前批次或型号不在允许清单）时整单拦截；
     * 返修中未回库或回库结论不合格的支架逐条拦截。
     */
    public BindCheckResultVO checkBatchBind(List<Long> bracketIds, Long equipmentId) {
        return doCheck(deduplicate(bracketIds), equipmentId, true);
    }

    /**
     * 单个绑定确认：确认前重新校验，仅绑定通过项。
     */
    @Transactional
    public BindConfirmResultVO confirmBind(Long bracketId, Long equipmentId) {
        BindCheckResultVO check = doCheck(List.of(bracketId), equipmentId, false);
        return doConfirm(check, equipmentId);
    }

    /**
     * 批量绑定确认：确认前重新校验（含换模批次闸门），仅绑定通过项。
     */
    @Transactional
    public BindConfirmResultVO confirmBatchBind(List<Long> bracketIds, Long equipmentId) {
        BindCheckResultVO check = doCheck(deduplicate(bracketIds), equipmentId, true);
        return doConfirm(check, equipmentId);
    }

    /**
     * 换线改挂预检：只允许选择源设备上已挂的支架，按目标机现行型号、长宽与容量规则逐项判定。
     * 纯校验不写库。容量按目标机当前占用累加本次改挂占用，与批量绑定同一口径。
     */
    public RehangCheckResultVO checkRehang(List<Long> bracketIds, Long sourceEquipmentId, Long targetEquipmentId) {
        return doRehangCheck(deduplicate(bracketIds), sourceEquipmentId, targetEquipmentId);
    }

    /**
     * 改挂确认：确认前重新预检，通过项在同一事务内直接把 equipmentId 从源设备改为目标设备，
     * 全程保持已绑定、不经过未绑定中间态；冲突项不动，仍留在源设备。
     */
    @Transactional
    public RehangConfirmResultVO confirmRehang(List<Long> bracketIds, Long sourceEquipmentId, Long targetEquipmentId) {
        RehangCheckResultVO check = doRehangCheck(deduplicate(bracketIds), sourceEquipmentId, targetEquipmentId);
        List<Long> passedIds = check.getPassedItems().stream()
                .map(BindCheckItemVO::getBracketId)
                .collect(Collectors.toList());
        List<BindCheckItemVO> conflicts = new ArrayList<>(check.getConflicts());
        if (passedIds.isEmpty()) {
            return new RehangConfirmResultVO(0, List.of(), conflicts);
        }
        List<Bracket> brackets = bracketRepository.findAllById(passedIds);
        List<Bracket> moved = new ArrayList<>();
        for (Bracket bracket : brackets) {
            // 以重新预检的结果为准：仅仍挂在源设备上的通过项才允许改挂
            if (sourceEquipmentId.equals(bracket.getEquipmentId())) {
                bracket.setEquipmentId(targetEquipmentId);
                moved.add(bracket);
            }
        }
        List<Bracket> saved = bracketRepository.saveAll(moved);
        List<BracketVO> voList = bracketService.convertToVOList(saved);
        return new RehangConfirmResultVO(voList.size(), voList, conflicts);
    }

    private RehangCheckResultVO doRehangCheck(List<Long> bracketIds, Long sourceEquipmentId, Long targetEquipmentId) {
        RehangCheckResultVO result = new RehangCheckResultVO();
        if (sourceEquipmentId != null) {
            result.setSourceEquipmentId(sourceEquipmentId);
        }
        if (targetEquipmentId != null) {
            result.setEquipmentId(targetEquipmentId);
        }

        Equipment source = sourceEquipmentId == null ? null
                : equipmentRepository.findById(sourceEquipmentId).orElse(null);
        Equipment target = targetEquipmentId == null ? null
                : equipmentRepository.findById(targetEquipmentId).orElse(null);

        if (source != null) {
            result.setSourceEquipmentCode(source.getEquipmentCode());
            result.setSourceEquipmentName(source.getEquipmentName());
        }

        if (sourceEquipmentId == null || targetEquipmentId == null) {
            return rehangAbort(result, 0, bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "请选择源设备和目标设备"))
                    .collect(Collectors.toList()));
        }
        if (source == null) {
            return rehangAbort(result, 0, bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "源设备不存在"))
                    .collect(Collectors.toList()));
        }
        if (target == null) {
            return rehangAbort(result, 0, bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "目标设备不存在"))
                    .collect(Collectors.toList()));
        }
        if (sourceEquipmentId.equals(targetEquipmentId)) {
            return rehangAbort(result, 0, bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "目标设备与源设备相同，无需改挂"))
                    .collect(Collectors.toList()));
        }

        result.setEquipmentCode(target.getEquipmentCode());
        result.setEquipmentName(target.getEquipmentName());
        result.setMaxBrackets(target.getMaxBrackets());

        // 换模批次放行闸门：目标机未写当前批次或批次型号不在允许清单时，整单拦截，不允许改挂
        MoldBatchGateVO gate = moldBatchService.evaluateGate(target);
        result.setMoldBatchGate(gate);
        if (!Boolean.TRUE.equals(gate.getPassed())) {
            return rehangAbort(result, 0, bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, gate.getReason()))
                    .collect(Collectors.toList()));
        }

        List<Bracket> targetBrackets = bracketRepository.findByEquipmentId(targetEquipmentId);
        int currentCount = targetBrackets.size();
        result.setCurrentCount(currentCount);
        // 本次从源设备腾出占用的通过项会在目标机新增占用；空表示不限容量
        Integer remainingSlots = target.getMaxBrackets() == null ? null
                : Math.max(0, target.getMaxBrackets() - currentCount);
        result.setAvailableSlots(remainingSlots);

        Map<Long, Bracket> bracketMap = bracketRepository.findAllById(bracketIds).stream()
                .collect(Collectors.toMap(Bracket::getId, Function.identity()));

        List<BindCheckItemVO> items = new ArrayList<>();
        for (Long bracketId : bracketIds) {
            Bracket bracket = bracketMap.get(bracketId);
            if (bracket == null) {
                items.add(new BindCheckItemVO(bracketId, null, null, null, null, false, "支架不存在"));
                continue;
            }
            BracketRepairGateVO repairGate = bracketRepairService.evaluateGate(bracket);
            String reason;
            if (bracket.getEquipmentId() == null) {
                reason = "支架当前未绑定，请使用批量绑定而非改挂";
            } else if (!sourceEquipmentId.equals(bracket.getEquipmentId())) {
                reason = "支架未挂在源设备上，无法改挂";
            } else if (repairGate != null && !Boolean.TRUE.equals(repairGate.getPassed())) {
                // 返修放行闸门：改挂只允许选择源设备上已挂支架，送修必先解绑，正常不会命中；
                // 数据异常（返修单未回库/回库不合格）时同样硬拦截，无法绕过预检。
                reason = repairGate.getReason();
            } else {
                reason = ruleMatcher.matchRule(target, bracket);
                if (reason == null && remainingSlots != null) {
                    if (remainingSlots <= 0) {
                        reason = "超出设备最大支架数量（上限" + target.getMaxBrackets() + "个，当前已占用" + currentCount + "个）";
                    } else {
                        remainingSlots--;
                    }
                }
            }
            BindCheckItemVO item = new BindCheckItemVO(
                    bracket.getId(),
                    bracket.getName(),
                    bracket.getModel(),
                    bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                    bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                    reason == null,
                    reason
            );
            item.setRepairGate(repairGate);
            items.add(item);
        }
        result.setItems(items);
        result.setPassedItems(items.stream().filter(BindCheckItemVO::getPassed).collect(Collectors.toList()));
        result.setConflicts(items.stream().filter(i -> !i.getPassed()).collect(Collectors.toList()));
        return result;
    }

    private RehangCheckResultVO rehangAbort(RehangCheckResultVO result, int currentCount, List<BindCheckItemVO> items) {
        result.setCurrentCount(currentCount);
        result.setAvailableSlots(null);
        result.setItems(items);
        result.setPassedItems(List.of());
        result.setConflicts(new ArrayList<>(items));
        return result;
    }

    private BindCheckResultVO doCheck(List<Long> bracketIds, Long equipmentId, boolean enforceBatchGates) {
        Equipment equipment = equipmentId == null ? null
                : equipmentRepository.findById(equipmentId).orElse(null);
        BindCheckResultVO result = new BindCheckResultVO();
        if (equipmentId != null) {
            result.setEquipmentId(equipmentId);
        }
        if (equipment == null) {
            result.setCurrentCount(0);
            result.setItems(bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "目标设备不存在"))
                    .collect(Collectors.toList()));
            result.setPassedItems(List.of());
            result.setConflicts(new ArrayList<>(result.getItems()));
            return result;
        }
        result.setEquipmentCode(equipment.getEquipmentCode());
        result.setEquipmentName(equipment.getEquipmentName());
        result.setMaxBrackets(equipment.getMaxBrackets());

        // 换模批次放行闸门（仅批量挂接强制）：未写当前批次或型号不在允许清单，整单判为冲突拦截
        MoldBatchGateVO gate = moldBatchService.evaluateGate(equipment);
        result.setMoldBatchGate(gate);
        if (enforceBatchGates && !Boolean.TRUE.equals(gate.getPassed())) {
            result.setCurrentCount(bracketRepository.findByEquipmentId(equipmentId).size());
            result.setAvailableSlots(null);
            List<BindCheckItemVO> blocked = bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, gate.getReason()))
                    .collect(Collectors.toList());
            result.setItems(blocked);
            result.setPassedItems(List.of());
            result.setConflicts(new ArrayList<>(blocked));
            return result;
        }

        List<Bracket> boundBrackets = bracketRepository.findByEquipmentId(equipmentId);
        int currentCount = boundBrackets.size();
        result.setCurrentCount(currentCount);

        Map<Long, Bracket> bracketMap = bracketRepository.findAllById(bracketIds).stream()
                .collect(Collectors.toMap(Bracket::getId, Function.identity()));

        // 容量：已在该设备上的支架重新绑定不额外占用；空表示不限容量
        Set<Long> alreadyOnEquipment = boundBrackets.stream()
                .map(Bracket::getId)
                .collect(Collectors.toSet());
        Integer remainingSlots = equipment.getMaxBrackets() == null ? null
                : Math.max(0, equipment.getMaxBrackets() - currentCount);
        result.setAvailableSlots(remainingSlots);

        List<BindCheckItemVO> items = new ArrayList<>();
        for (Long bracketId : bracketIds) {
            Bracket bracket = bracketMap.get(bracketId);
            if (bracket == null) {
                items.add(new BindCheckItemVO(bracketId, null, null, null, null, false, "支架不存在"));
                continue;
            }
            BracketRepairGateVO repairGate = enforceBatchGates
                    ? bracketRepairService.evaluateGate(bracket) : null;
            String reason = null;
            // 返修放行闸门（仅批量挂接强制，单个绑定不走）：返修中未回库/回库不合格直接判冲突，
            // 写明原因，且不占用容量、不进入型号尺寸判定。
            if (repairGate != null && !Boolean.TRUE.equals(repairGate.getPassed())) {
                reason = repairGate.getReason();
            }
            if (reason == null) {
                reason = ruleMatcher.matchRule(equipment, bracket);
            }
            if (reason == null && !alreadyOnEquipment.contains(bracketId)) {
                if (remainingSlots != null && remainingSlots <= 0) {
                    reason = "超出设备最大支架数量（上限" + equipment.getMaxBrackets() + "个，当前已占用" + currentCount + "个）";
                } else if (remainingSlots != null) {
                    remainingSlots--;
                }
            }
            BindCheckItemVO item = new BindCheckItemVO(
                    bracket.getId(),
                    bracket.getName(),
                    bracket.getModel(),
                    bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                    bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                    reason == null,
                    reason
            );
            item.setRepairGate(repairGate);
            items.add(item);
        }
        result.setItems(items);
        result.setPassedItems(items.stream().filter(BindCheckItemVO::getPassed).collect(Collectors.toList()));
        result.setConflicts(items.stream().filter(i -> !i.getPassed()).collect(Collectors.toList()));
        return result;
    }

    private BindConfirmResultVO doConfirm(BindCheckResultVO check, Long equipmentId) {
        List<Long> passedIds = check.getPassedItems().stream()
                .map(BindCheckItemVO::getBracketId)
                .collect(Collectors.toList());
        if (passedIds.isEmpty()) {
            return new BindConfirmResultVO(0, List.of());
        }
        List<Bracket> brackets = bracketRepository.findAllById(passedIds);
        for (Bracket bracket : brackets) {
            bracket.setEquipmentId(equipmentId);
        }
        List<Bracket> saved = bracketRepository.saveAll(brackets);
        List<BracketVO> voList = bracketService.convertToVOList(saved);
        return new BindConfirmResultVO(voList.size(), voList);
    }

    private List<Long> deduplicate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }
}
