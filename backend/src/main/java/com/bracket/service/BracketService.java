package com.bracket.service;

import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.BracketRepairRecord;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BracketService {

    private static final Logger log = LoggerFactory.getLogger(BracketService.class);

    private final BracketRepository bracketRepository;
    private final EquipmentRepository equipmentRepository;
    private final BracketRepairService bracketRepairService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String POPULAR_MODELS_KEY = "bracket:models:popular";

    /**
     * 缓存兜底过期时间，正常情况下支架增删改后会主动失效缓存。
     */
    private static final Duration POPULAR_MODELS_TTL = Duration.ofMinutes(10);

    @Autowired
    public BracketService(BracketRepository bracketRepository, EquipmentRepository equipmentRepository,
                          BracketRepairService bracketRepairService,
                          RedisTemplate<String, Object> redisTemplate) {
        this.bracketRepository = bracketRepository;
        this.equipmentRepository = equipmentRepository;
        this.bracketRepairService = bracketRepairService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 支架档案分页查询。
     *
     * 返修状态过滤只认每个支架「当前返修单」（送修时间最新的一张，同时间以 ID 大者为准），
     * 与返修放行闸门同一口径：
     * REPAIRING=当前单未写回库结论；RETURNED_UNQUALIFIED=当前单结论不合格；
     * RETURNED_QUALIFIED=当前单结论合格；无当前单（从未返修）不属于任何一种状态。
     * 旧返修单已被更新的返修单覆盖时，即使历史上有过不合格结论也不再命中，
     * 避免"已回库合格的支架仍出现在回库不合格列表"。
     */
    public PageResult<BracketVO> findAll(String name, String model, Integer bindStatus,
                                         String repairStatus, Pageable pageable) {
        final String nameParam = name;
        final String modelParam = model;
        final Integer bindStatusParam = bindStatus;
        final String statusParam = normalizeRepairStatus(repairStatus);

        Specification<Bracket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nameParam != null && !nameParam.trim().isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + nameParam + "%"));
            }
            if (modelParam != null && !modelParam.trim().isEmpty()) {
                predicates.add(cb.like(root.get("model"), "%" + modelParam + "%"));
            }
            if (bindStatusParam != null && bindStatusParam == 0) {
                predicates.add(cb.isNull(root.get("equipmentId")));
            } else if (bindStatusParam != null && bindStatusParam == 1) {
                predicates.add(cb.isNotNull(root.get("equipmentId")));
            }
            if (statusParam != null) {
                predicates.add(currentRepairStatusPredicate(root, query, cb, statusParam));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Bracket> page = bracketRepository.findAll(spec, pageable);
        List<BracketVO> voList = convertToVOList(page.getContent());
        return new PageResult<>(voList, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 只放行三种已知返修状态；未知/空白值视为不过滤，避免非法参数导致空结果或报错。 */
    private String normalizeRepairStatus(String repairStatus) {
        if (repairStatus == null) {
            return null;
        }
        String trimmed = repairStatus.trim();
        return switch (trimmed) {
            case BracketRepairService.STATUS_REPAIRING,
                 BracketRepairService.STATUS_RETURNED_QUALIFIED,
                 BracketRepairService.STATUS_RETURNED_UNQUALIFIED -> trimmed;
            default -> null;
        };
    }

    /**
     * 构造「当前返修单状态 = statusParam」的断言。
     * 存在一张返修单 r：属于该支架、不存在比它更新的返修单、且 r 的结论符合目标状态。
     * 返修中=return_result IS NULL；合格/不合格=return_result = TRUE/FALSE。
     */
    private Predicate currentRepairStatusPredicate(
            jakarta.persistence.criteria.Root<Bracket> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String statusParam) {
        Subquery<BracketRepairRecord> currentSub = query.subquery(BracketRepairRecord.class);
        jakarta.persistence.criteria.Root<BracketRepairRecord> r =
                currentSub.from(BracketRepairRecord.class);
        currentSub.select(r);

        // 不存在更新的返修单：送修时间更晚，或同时间 ID 更大（与 findFirst...IdDesc 兜底一致）
        Subquery<Long> newerSub = currentSub.subquery(Long.class);
        jakarta.persistence.criteria.Root<BracketRepairRecord> newer =
                newerSub.from(BracketRepairRecord.class);
        newerSub.select(newer.get("id"))
                .where(cb.and(
                        cb.equal(newer.get("bracketId"), root.get("id")),
                        cb.or(
                                cb.greaterThan(newer.get("repairTime"), r.get("repairTime")),
                                cb.and(
                                        cb.equal(newer.get("repairTime"), r.get("repairTime")),
                                        cb.greaterThan(newer.get("id"), r.get("id"))
                                )
                        )
                ));
        currentSub.where(cb.and(
                cb.equal(r.get("bracketId"), root.get("id")),
                cb.not(cb.exists(newerSub)),
                BracketRepairService.STATUS_REPAIRING.equals(statusParam)
                        ? cb.isNull(r.get("returnResult"))
                        : cb.equal(r.get("returnResult"),
                        BracketRepairService.STATUS_RETURNED_QUALIFIED.equals(statusParam))
        ));
        return cb.exists(currentSub);
    }

    public BracketVO findById(Long id) {
        Bracket bracket = bracketRepository.findById(id).orElse(null);
        if (bracket == null) {
            return null;
        }
        return convertToVO(bracket);
    }

    @Transactional
    public BracketVO save(com.bracket.dto.BracketCreateRequest request) {
        Bracket bracket = new Bracket();
        bracket.setName(request.getName());
        bracket.setModel(request.getModel());
        bracket.setLengthMm(request.getLengthMm());
        bracket.setWidthMm(request.getWidthMm());
        Bracket saved = bracketRepository.save(bracket);
        // 新型号会出现在热门型号建议中，事务提交后失效缓存
        evictPopularModelsCacheAfterCommit();
        return convertToVO(saved);
    }

    /**
     * 批量导入专用：仅保存已逐行校验通过的支架，整体一个事务，
     * 提交后失效热门型号缓存（导入会引入新型号）。
     */
    @Transactional
    public List<Bracket> saveAllForImport(List<Bracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return List.of();
        }
        List<Bracket> saved = bracketRepository.saveAll(brackets);
        evictPopularModelsCacheAfterCommit();
        return saved;
    }

    @Transactional
    public BracketVO update(Long id, com.bracket.dto.BracketCreateRequest request) {
        Bracket existing = bracketRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        boolean modelChanged = !java.util.Objects.equals(existing.getModel(), request.getModel());
        existing.setName(request.getName());
        existing.setModel(request.getModel());
        existing.setLengthMm(request.getLengthMm());
        existing.setWidthMm(request.getWidthMm());
        Bracket updated = bracketRepository.save(existing);
        // 仅型号发生变化（改名）时才需要失效，改名后旧型号不应残留在建议中
        if (modelChanged) {
            evictPopularModelsCacheAfterCommit();
        }
        return convertToVO(updated);
    }

    @Transactional
    public void delete(Long id) {
        Bracket bracket = bracketRepository.findById(id).orElse(null);
        if (bracket == null) {
            return;
        }
        bracket.setEquipmentId(null);
        bracketRepository.save(bracket);
        bracketRepository.delete(bracket);
        // 删除后该型号可能已不存在于任何支架，事务提交后失效缓存
        evictPopularModelsCacheAfterCommit();
    }

    /**
     * 在当前事务成功提交后失效热门型号缓存，避免"先删缓存、后提交事务"
     * 期间并发请求把旧数据重新写回缓存。Redis 异常不影响支架数据的保存。
     */
    private void evictPopularModelsCacheAfterCommit() {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictPopularModelsCache();
                    }
                });
            } else {
                evictPopularModelsCache();
            }
        } catch (Exception e) {
            log.warn("注册热门型号缓存失效回调失败", e);
        }
    }

    private void evictPopularModelsCache() {
        try {
            redisTemplate.delete(POPULAR_MODELS_KEY);
        } catch (Exception e) {
            // 缓存失效失败不应影响支架的新增、编辑或删除
            log.warn("失效热门型号缓存失败，key={}", POPULAR_MODELS_KEY, e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getPopularModels() {
        List<Object> cached = null;
        try {
            cached = redisTemplate.opsForList().range(POPULAR_MODELS_KEY, 0, -1);
        } catch (Exception e) {
            // Redis 不可用时降级为直接查询数据库
            log.warn("读取热门型号缓存失败，降级查询数据库", e);
        }
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Object::toString).collect(Collectors.toList());
        }
        List<String> models = loadPopularModelsFromDb();
        cachePopularModels(models);
        return models;
    }

    private List<String> loadPopularModelsFromDb() {
        return bracketRepository.findAll().stream()
                .map(Bracket::getModel)
                .distinct()
                .collect(Collectors.toList());
    }

    private void cachePopularModels(List<String> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForList().rightPushAll(POPULAR_MODELS_KEY, models.toArray());
            redisTemplate.expire(POPULAR_MODELS_KEY, POPULAR_MODELS_TTL);
        } catch (Exception e) {
            // 缓存写入失败不影响本次查询结果
            log.warn("写入热门型号缓存失败，key={}", POPULAR_MODELS_KEY, e);
        }
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        long total = bracketRepository.count();
        long bound = bracketRepository.countByEquipmentIdIsNotNull();
        long unbound = bracketRepository.countByEquipmentIdIsNull();
        stats.put("total", total);
        stats.put("bound", bound);
        stats.put("unbound", unbound);
        return stats;
    }

    public BracketVO convertToVO(Bracket bracket) {
        if (bracket == null) {
            return null;
        }
        String equipmentName = null;
        if (bracket.getEquipmentId() != null) {
            Equipment equipment = equipmentRepository.findById(bracket.getEquipmentId()).orElse(null);
            if (equipment != null) {
                equipmentName = equipment.getEquipmentName();
            }
        }
        BracketVO vo = new BracketVO(
                bracket.getId(),
                bracket.getName(),
                bracket.getModel(),
                bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                bracket.getEquipmentId(),
                equipmentName,
                bracket.getCreateTime(),
                bracket.getUpdateTime()
        );
        applyRepairInfo(vo, bracketRepairService.findCurrentRecord(bracket.getId()));
        return vo;
    }

    public List<BracketVO> convertToVOList(List<Bracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return List.of();
        }
        List<Long> equipmentIds = brackets.stream()
                .map(Bracket::getEquipmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> equipmentNameMap = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            List<Equipment> equipments = equipmentRepository.findAllById(equipmentIds);
            for (Equipment e : equipments) {
                equipmentNameMap.put(e.getId(), e.getEquipmentName());
            }
        }
        List<Long> bracketIds = brackets.stream().map(Bracket::getId).collect(Collectors.toList());
        Map<Long, BracketRepairRecord> currentRepairMap = bracketRepairService.findCurrentRecordMap(bracketIds);
        return brackets.stream()
                .map(b -> {
                    BracketVO vo = new BracketVO();
                    vo.setId(b.getId());
                    vo.setName(b.getName());
                    vo.setModel(b.getModel());
                    vo.setLength(b.getLengthMm() != null ? b.getLengthMm().doubleValue() : null);
                    vo.setWidth(b.getWidthMm() != null ? b.getWidthMm().doubleValue() : null);
                    vo.setEquipmentId(b.getEquipmentId());
                    vo.setEquipmentName(b.getEquipmentId() != null ? equipmentNameMap.get(b.getEquipmentId()) : null);
                    vo.setCreateTime(b.getCreateTime());
                    vo.setUpdateTime(b.getUpdateTime());
                    applyRepairInfo(vo, currentRepairMap.get(b.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 写入支架当前返修状态：返修中/已回库合格/已回库不合格；无返修单时字段留空。 */
    private void applyRepairInfo(BracketVO vo, BracketRepairRecord current) {
        if (current == null) {
            return;
        }
        vo.setCurrentRepairId(current.getId());
        vo.setCurrentRepairNo(current.getRepairNo());
        vo.setRepairStatus(BracketRepairService.resolveStatus(current));
        vo.setReturnResult(current.getReturnResult());
        vo.setInspector(current.getInspector());
    }
}
