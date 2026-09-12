package com.bracket.repository;

import com.bracket.entity.MoldBatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoldBatchRecordRepository extends JpaRepository<MoldBatchRecord, Long> {

    /** 按设备翻历史换模记录，换模时间新的在前（同时间以登记记录 ID 倒序兜底）。 */
    Page<MoldBatchRecord> findByEquipmentIdOrderByChangeTimeDescIdDesc(Long equipmentId, Pageable pageable);

    /** 设备的最新一条换模记录，即当前批次；不存在时为空（从未登记）。 */
    Optional<MoldBatchRecord> findFirstByEquipmentIdOrderByChangeTimeDescIdDesc(Long equipmentId);

    List<MoldBatchRecord> findByEquipmentIdOrderByChangeTimeDescIdDesc(Long equipmentId);
}
