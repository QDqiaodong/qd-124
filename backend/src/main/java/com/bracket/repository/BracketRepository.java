package com.bracket.repository;

import com.bracket.entity.Bracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BracketRepository extends JpaRepository<Bracket, Long>, JpaSpecificationExecutor<Bracket> {

    List<Bracket> findByEquipmentId(Long equipmentId);

    long countByEquipmentIdIsNotNull();

    long countByEquipmentIdIsNull();

    List<Bracket> findByModelIn(List<String> models);
}
