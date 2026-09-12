package com.bracket.service;

import com.bracket.dto.BracketCreateRequest;
import com.bracket.entity.Bracket;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热门型号 Redis 缓存失效回归测试：
 * 支架新增、改名、删除后下一次查询必须反映最新数据；Redis 异常不影响支架保存。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BracketServiceTest {

    private static final String POPULAR_MODELS_KEY = "bracket:models:popular";

    @Mock
    private BracketRepository bracketRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private BracketRepairService bracketRepairService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @InjectMocks
    private BracketService bracketService;

    private Bracket bracket(Long id, String name, String model) {
        Bracket b = new Bracket();
        b.setId(id);
        b.setName(name);
        b.setModel(model);
        return b;
    }

    private BracketCreateRequest request(String name, String model) {
        BracketCreateRequest req = new BracketCreateRequest();
        req.setName(name);
        req.setModel(model);
        req.setLength(100.0);
        req.setWidth(50.0);
        return req;
    }

    /**
     * 触发事务提交后的回调（生产代码在事务提交后才删除缓存）。
     */
    @SuppressWarnings("unchecked")
    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    @BeforeEach
    void setUp() {
        // 开启事务同步上下文，使服务层注册 afterCommit 回调
        TransactionSynchronizationManager.initSynchronization();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getPopularModels_returnsCachedData_whenCacheHit() {
        when(listOperations.range(POPULAR_MODELS_KEY, 0, -1))
                .thenReturn(List.of("A-01", "B-02"));

        List<String> models = bracketService.getPopularModels();

        assertEquals(List.of("A-01", "B-02"), models);
        verify(bracketRepository, never()).findAll();
    }

    @Test
    void getPopularModels_queriesDbAndRefillsCache_onCacheMiss() {
        when(listOperations.range(POPULAR_MODELS_KEY, 0, -1)).thenReturn(List.of());
        when(bracketRepository.findAll())
                .thenReturn(List.of(bracket(1L, "支架1", "A-01"), bracket(2L, "支架2", "B-02")));

        List<String> models = bracketService.getPopularModels();

        assertEquals(List.of("A-01", "B-02"), models);
        verify(listOperations).rightPushAll(eq(POPULAR_MODELS_KEY), any(Object[].class));
        verify(redisTemplate).expire(eq(POPULAR_MODELS_KEY), any());
    }

    @Test
    void getPopularModels_fallsBackToDb_whenRedisReadFails() {
        when(listOperations.range(POPULAR_MODELS_KEY, 0, -1))
                .thenThrow(new RuntimeException("connection refused"));
        when(bracketRepository.findAll())
                .thenReturn(List.of(bracket(1L, "支架1", "A-01")));

        List<String> models = bracketService.getPopularModels();

        assertEquals(List.of("A-01"), models);
    }

    /**
     * 用状态化的 List 模拟 Redis 中的热门型号缓存：delete 清空、range 读当前内容、
     * rightPushAll 回填。这样可以真实验证"失效后下一次查询回库重建缓存"的完整链路。
     */
    private void stubPopularModelsCache(List<String> seed) {
        List<String> cachedModels = new java.util.ArrayList<>(seed);
        when(redisTemplate.delete(POPULAR_MODELS_KEY)).thenAnswer(inv -> {
            cachedModels.clear();
            return true;
        });
        when(listOperations.range(POPULAR_MODELS_KEY, 0, -1))
                .thenAnswer(inv -> List.copyOf(cachedModels));
        when(listOperations.rightPushAll(eq(POPULAR_MODELS_KEY), any(Object[].class)))
                .thenAnswer(inv -> {
                    // vararg 会被 Mockito 展开，getRawArguments 的第 1 位起为各型号
                    Object[] rawArgs = inv.getRawArguments();
                    int pushed = 0;
                    for (int i = 1; i < rawArgs.length; i++) {
                        cachedModels.add(rawArgs[i].toString());
                        pushed++;
                    }
                    return (long) pushed;
                });
        when(redisTemplate.expire(eq(POPULAR_MODELS_KEY), any())).thenReturn(true);
    }

    @Test
    void save_evictsCacheAfterCommit_soNextQueryReflectsNewModel() {
        when(bracketRepository.save(any(Bracket.class)))
                .thenAnswer(inv -> {
                    Bracket b = inv.getArgument(0);
                    b.setId(9L);
                    return b;
                });
        stubPopularModelsCache(List.of("A-01"));
        when(bracketRepository.findAll())
                .thenReturn(List.of(bracket(1L, "支架1", "A-01"), bracket(9L, "新支架", "C-03")));

        BracketVO saved = bracketService.save(request("新支架", "C-03"));
        assertEquals(9L, saved.getId());
        // 提交前缓存仍在
        verify(redisTemplate, never()).delete(POPULAR_MODELS_KEY);

        triggerAfterCommit();
        verify(redisTemplate, times(1)).delete(POPULAR_MODELS_KEY);

        List<String> modelsAfterSave = bracketService.getPopularModels();
        assertTrue(modelsAfterSave.contains("C-03"), "新增支架的型号应出现在热门型号建议中");
        assertEquals(List.of("A-01", "C-03"), modelsAfterSave);
    }

    @Test
    void update_renameEvictsCacheAfterCommit_soOldModelDoesNotLinger() {
        Bracket existing = bracket(1L, "支架1", "OLD-MODEL");
        when(bracketRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bracketRepository.save(any(Bracket.class))).thenAnswer(inv -> inv.getArgument(0));
        stubPopularModelsCache(List.of("OLD-MODEL"));
        // 旧型号已随改名消失，库里只剩 NEW-MODEL
        when(bracketRepository.findAll()).thenReturn(List.of(bracket(1L, "支架1", "NEW-MODEL")));

        bracketService.update(1L, request("支架1", "NEW-MODEL"));
        triggerAfterCommit();
        verify(redisTemplate, times(1)).delete(POPULAR_MODELS_KEY);

        List<String> modelsAfterRename = bracketService.getPopularModels();
        assertEquals(List.of("NEW-MODEL"), modelsAfterRename);
        assertTrue(!modelsAfterRename.contains("OLD-MODEL"), "改名后旧型号不得残留在建议中");
    }

    @Test
    void update_withoutModelChange_doesNotEvictCache() {
        Bracket existing = bracket(1L, "旧名称", "A-01");
        when(bracketRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bracketRepository.save(any(Bracket.class))).thenAnswer(inv -> inv.getArgument(0));

        bracketService.update(1L, request("新名称", "A-01"));
        triggerAfterCommit();

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void update_notFound_returnsNullAndDoesNotEvictCache() {
        when(bracketRepository.findById(404L)).thenReturn(Optional.empty());

        BracketVO result = bracketService.update(404L, request("x", "X-1"));

        assertNull(result);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void delete_evictsCacheAfterCommit_soStaleModelDisappears() {
        // 唯一使用 Z-09 型号的支架被删除后，Z-09 不应再出现在建议中
        when(bracketRepository.findById(3L)).thenReturn(Optional.of(bracket(3L, "支架3", "Z-09")));
        stubPopularModelsCache(List.of("A-01", "Z-09"));
        when(bracketRepository.findAll()).thenReturn(List.of(bracket(1L, "支架1", "A-01")));

        bracketService.delete(3L);
        triggerAfterCommit();

        verify(bracketRepository).delete(any(Bracket.class));
        verify(redisTemplate, times(1)).delete(POPULAR_MODELS_KEY);

        List<String> modelsAfterDelete = bracketService.getPopularModels();
        assertEquals(List.of("A-01"), modelsAfterDelete);
        assertTrue(!modelsAfterDelete.contains("Z-09"), "删除支架后不存在的型号不得残留");
    }

    @Test
    void delete_notFound_doesNothingAndDoesNotEvictCache() {
        when(bracketRepository.findById(404L)).thenReturn(Optional.empty());

        bracketService.delete(404L);

        verify(bracketRepository, never()).delete(any(Bracket.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void redisFailureOnEviction_doesNotAffectBracketSave() {
        when(bracketRepository.save(any(Bracket.class)))
                .thenAnswer(inv -> {
                    Bracket b = inv.getArgument(0);
                    b.setId(9L);
                    return b;
                });
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(POPULAR_MODELS_KEY);

        BracketVO saved = bracketService.save(request("支架", "A-01"));
        // afterCommit 中删除缓存抛异常被吞掉，支架依然保存成功
        triggerAfterCommit();

        assertEquals(9L, saved.getId());
        verify(bracketRepository, times(1)).save(any(Bracket.class));
    }

    @Test
    void redisFailureOnCacheWrite_doesNotAffectPopularModelsQuery() {
        when(listOperations.range(POPULAR_MODELS_KEY, 0, -1)).thenReturn(List.of());
        when(bracketRepository.findAll()).thenReturn(List.of(bracket(1L, "支架1", "A-01")));
        doThrow(new RuntimeException("redis down"))
                .when(listOperations).rightPushAll(eq(POPULAR_MODELS_KEY), any(Object[].class));

        List<String> models = bracketService.getPopularModels();

        assertEquals(List.of("A-01"), models);
    }
}
