package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.repository.BracketRepository;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BindingService {

    private final BracketRepository bracketRepository;
    private final BracketService bracketService;

    @Autowired
    public BindingService(BracketRepository bracketRepository, BracketService bracketService) {
        this.bracketRepository = bracketRepository;
        this.bracketService = bracketService;
    }

    @Transactional
    public BracketVO bind(Long bracketId, Long equipmentId) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        bracket.setEquipmentId(equipmentId);
        Bracket saved = bracketRepository.save(bracket);
        return bracketService.convertToVO(saved);
    }

    @Transactional
    public BracketVO unbind(Long bracketId) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        bracket.setEquipmentId(null);
        Bracket saved = bracketRepository.save(bracket);
        return bracketService.convertToVO(saved);
    }

    @Transactional
    public void batchBind(List<Long> bracketIds, Long equipmentId) {
        List<Bracket> brackets = bracketRepository.findAllById(bracketIds);
        for (Bracket bracket : brackets) {
            bracket.setEquipmentId(equipmentId);
        }
        bracketRepository.saveAll(brackets);
    }
}
