package com.bracket.repository;

import com.bracket.entity.LubricationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LubricationRecordRepository extends JpaRepository<LubricationRecord, Long> {

    /** 按设备翻历史润滑单，润滑时间新的在前（同时间以登记记录 ID 倒序兜底）。 */
    Page<LubricationRecord> findByEquipmentIdOrderByLubricationTimeDescIdDesc(Long equipmentId, Pageable pageable);

    /** 设备指定班次内最新一张润滑单；本班未登记时为空。 */
    Optional<LubricationRecord> findFirstByEquipmentIdAndShiftKeyOrderByLubricationTimeDescIdDesc(
            Long equipmentId, String shiftKey);
}
