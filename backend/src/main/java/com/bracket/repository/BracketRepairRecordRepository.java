package com.bracket.repository;

import com.bracket.entity.BracketRepairRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BracketRepairRecordRepository extends JpaRepository<BracketRepairRecord, Long> {

    /** 按支架翻返修单，送修时间新的在前（同时间以记录 ID 倒序兜底）。 */
    Page<BracketRepairRecord> findByBracketIdOrderByRepairTimeDescIdDesc(Long bracketId, Pageable pageable);

    /** 支架的最新一张返修单，即当前返修状态；从未返修时为空。 */
    Optional<BracketRepairRecord> findFirstByBracketIdOrderByRepairTimeDescIdDesc(Long bracketId);

    List<BracketRepairRecord> findByBracketIdOrderByRepairTimeDescIdDesc(Long bracketId);
}
