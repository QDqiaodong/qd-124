package com.bracket.repository;

import com.bracket.entity.FirstArticleInspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FirstArticleInspectionRepository extends JpaRepository<FirstArticleInspection, Long> {

    /** 全部确认单，开单时间新的在前（同时间以 ID 倒序兜底）。 */
    Page<FirstArticleInspection> findAllByOrderByCreateTimeDescIdDesc(Pageable pageable);

    /** 按机台筛确认单，开单时间新的在前。 */
    Page<FirstArticleInspection> findByEquipmentIdOrderByCreateTimeDescIdDesc(Long equipmentId, Pageable pageable);

    /** 按放行结果筛确认单，开单时间新的在前。 */
    Page<FirstArticleInspection> findByStatusOrderByCreateTimeDescIdDesc(String status, Pageable pageable);

    /** 按机台 + 放行结果组合筛选。 */
    Page<FirstArticleInspection> findByEquipmentIdAndStatusOrderByCreateTimeDescIdDesc(
            Long equipmentId, String status, Pageable pageable);
}
