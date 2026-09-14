package com.bracket.repository;

import com.bracket.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /**
     * 设备配套清单分页查询（编号/名称模糊搜索 + 「只看超额」过滤）。
     *
     * 「超额」口径必须与设备卡片 capacityStatus=exceeded 完全一致：
     * 上限非空，且该设备当前已绑定支架数严格大于上限。
     * 过滤直接写在分页 SQL 内（数据库分页），刷新、翻页都返回同一份名单，
     * 不能由前端藏卡片——否则当前页少的卡片会与 total、后续页对不上。
     *
     * 用原生 SQL 并让 count(子查询) 与 INT 列直接做 BIGINT 隐式比较，
     * 避免 JPQL/Criteria 在 H2 与 MySQL 上为 Integer 列与 Long 计数的
     * 跨类型比较生成方言不兼容的 CAST（MySQL 只认 SIGNED，H2 只认 BIGINT）。
     * 未开启过滤时用 1=0 短路，不产生任何超额比较。
     */
    @Query(value = "SELECT e.* FROM equipment e WHERE "
            + "(:code IS NULL OR e.equipment_code LIKE CONCAT('%', :code, '%')) "
            + "AND (:name IS NULL OR e.equipment_name LIKE CONCAT('%', :name, '%')) "
            + "AND (:onlyExceeded = FALSE OR ("
            + "e.max_brackets IS NOT NULL "
            + "AND (SELECT COUNT(*) FROM bracket b WHERE b.equipment_id = e.id) > e.max_brackets)) "
            + "ORDER BY e.create_time DESC, e.id DESC",
            countQuery = "SELECT COUNT(*) FROM equipment e WHERE "
                    + "(:code IS NULL OR e.equipment_code LIKE CONCAT('%', :code, '%')) "
                    + "AND (:name IS NULL OR e.equipment_name LIKE CONCAT('%', :name, '%')) "
                    + "AND (:onlyExceeded = FALSE OR ("
                    + "e.max_brackets IS NOT NULL "
                    + "AND (SELECT COUNT(*) FROM bracket b WHERE b.equipment_id = e.id) > e.max_brackets))",
            nativeQuery = true)
    Page<Equipment> findPage(@Param("code") String code,
                             @Param("name") String name,
                             @Param("onlyExceeded") boolean onlyExceeded,
                             Pageable pageable);
}
