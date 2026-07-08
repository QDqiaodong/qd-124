package com.bracket.repository;

import com.bracket.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    org.springframework.data.domain.Page<Equipment> findByEquipmentCodeContaining(String code, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Equipment> findByEquipmentNameContaining(String name, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Equipment> findByEquipmentCodeContainingAndEquipmentNameContaining(String code, String name, org.springframework.data.domain.Pageable pageable);
}
