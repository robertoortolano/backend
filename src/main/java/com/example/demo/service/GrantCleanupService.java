package com.example.demo.service;

import com.example.demo.entity.Grant;
import com.example.demo.repository.GrantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrantCleanupService {
    
    private final EntityManager entityManager;
    private final GrantRepository grantRepository;
    
    @Transactional
    public void deleteGrantCompletely(Long grantId) {
        if (grantId == null) {
            return;
        }
        
        Grant grant = grantRepository.findByIdWithCollections(grantId)
                .orElseGet(() -> entityManager.find(Grant.class, grantId));
        
        if (grant == null) {
            return;
        }
        
        // Scollega tutte le relazioni many-to-many in modo sicuro
        grant.getUsers().clear();
        grant.getGroups().clear();
        grant.getNegatedUsers().clear();
        grant.getNegatedGroups().clear();
        grantRepository.save(grant);
        
        // Assicurati che il Grant sia gestito dall'EntityManager prima della rimozione
        Grant managed = entityManager.contains(grant) ? grant : entityManager.merge(grant);
        entityManager.remove(managed);
        entityManager.flush();
    }
}

