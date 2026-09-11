package com.bracket.repository;

import com.bracket.entity.Bracket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BracketRepository extends JpaRepository<Bracket, Long> {

    List<Bracket> findByEquipmentId(Long equipmentId);

    Page<Bracket> findByNameContaining(String name, Pageable pageable);

    Page<Bracket> findByModelContaining(String model, Pageable pageable);

    Page<Bracket> findByNameContainingAndModelContaining(String name, String model, Pageable pageable);

    Page<Bracket> findByEquipmentIdIsNull(Pageable pageable);

    Page<Bracket> findByEquipmentIdIsNotNull(Pageable pageable);

    Page<Bracket> findByNameContainingAndEquipmentIdIsNull(String name, Pageable pageable);

    Page<Bracket> findByNameContainingAndEquipmentIdIsNotNull(String name, Pageable pageable);

    Page<Bracket> findByModelContainingAndEquipmentIdIsNull(String model, Pageable pageable);

    Page<Bracket> findByModelContainingAndEquipmentIdIsNotNull(String model, Pageable pageable);

    Page<Bracket> findByNameContainingAndModelContainingAndEquipmentIdIsNull(String name, String model, Pageable pageable);

    Page<Bracket> findByNameContainingAndModelContainingAndEquipmentIdIsNotNull(String name, String model, Pageable pageable);

    long countByEquipmentIdIsNotNull();

    long countByEquipmentIdIsNull();

    List<Bracket> findByModelIn(List<String> models);
}
