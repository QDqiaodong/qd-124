package com.bracket.service;

import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BracketService {

    private static final Logger log = LoggerFactory.getLogger(BracketService.class);

    private final BracketRepository bracketRepository;
    private final EquipmentRepository equipmentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String POPULAR_MODELS_KEY = "bracket:models:popular";

    /**
     * 缓存兜底过期时间，正常情况下支架增删改后会主动失效缓存。
     */
    private static final Duration POPULAR_MODELS_TTL = Duration.ofMinutes(10);

    @Autowired
    public BracketService(BracketRepository bracketRepository, EquipmentRepository equipmentRepository, RedisTemplate<String, Object> redisTemplate) {
        this.bracketRepository = bracketRepository;
        this.equipmentRepository = equipmentRepository;
        this.redisTemplate = redisTemplate;
    }

    public PageResult<BracketVO> findAll(String name, String model, Integer bindStatus, Pageable pageable) {
        Page<Bracket> page;
        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasModel = model != null && !model.trim().isEmpty();
        boolean onlyUnbound = bindStatus != null && bindStatus == 0;
        boolean onlyBound = bindStatus != null && bindStatus == 1;
        if (onlyUnbound) {
            if (hasName && hasModel) {
                page = bracketRepository.findByNameContainingAndModelContainingAndEquipmentIdIsNull(name, model, pageable);
            } else if (hasName) {
                page = bracketRepository.findByNameContainingAndEquipmentIdIsNull(name, pageable);
            } else if (hasModel) {
                page = bracketRepository.findByModelContainingAndEquipmentIdIsNull(model, pageable);
            } else {
                page = bracketRepository.findByEquipmentIdIsNull(pageable);
            }
        } else if (onlyBound) {
            if (hasName && hasModel) {
                page = bracketRepository.findByNameContainingAndModelContainingAndEquipmentIdIsNotNull(name, model, pageable);
            } else if (hasName) {
                page = bracketRepository.findByNameContainingAndEquipmentIdIsNotNull(name, pageable);
            } else if (hasModel) {
                page = bracketRepository.findByModelContainingAndEquipmentIdIsNotNull(model, pageable);
            } else {
                page = bracketRepository.findByEquipmentIdIsNotNull(pageable);
            }
        } else if (!hasName && !hasModel) {
            page = bracketRepository.findAll(pageable);
        } else if (hasName && !hasModel) {
            page = bracketRepository.findByNameContaining(name, pageable);
        } else if (!hasName) {
            page = bracketRepository.findByModelContaining(model, pageable);
        } else {
            page = bracketRepository.findByNameContainingAndModelContaining(name, model, pageable);
        }
        List<BracketVO> voList = convertToVOList(page.getContent());
        return new PageResult<>(voList, page.getTotalElements(), page.getNumber() + 1, page.getSize());
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
        return new BracketVO(
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
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
