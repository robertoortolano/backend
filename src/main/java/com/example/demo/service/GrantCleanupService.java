package com.example.demo.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrantCleanupService {
    
    private final EntityManager entityManager;
    
    /**
     * Elimina una grant con una transazione completamente separata
     * per evitare conflitti con Hibernate Session.
     * 
     * IMPORTANTE: Usa REQUIRES_NEW per isolare la transazione dalla transazione principale.
     * Le eccezioni devono essere gestite dal chiamante per non compromettere la transazione principale.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteGrantCompletely(Long grantId) {
        if (grantId == null) {
            return;
        }
        
        // Elimina dalle tabelle di join ManyToMany
        // Hibernate genera i nomi delle colonne come: [entity_field]_[id] e [target_entity]_id
        try {
            // Prova prima con i nomi standard di Hibernate
            entityManager.createNativeQuery("DELETE FROM grant_assignment_users WHERE grant_assignment_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
            // Se fallisce, prova con nomi alternativi
            try {
                entityManager.createNativeQuery("DELETE FROM grant_assignment_users WHERE grant_id = :grantId")
                    .setParameter("grantId", grantId).executeUpdate();
            } catch (Exception e2) {
            }
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM grant_assignment_groups WHERE grant_assignment_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
            try {
                entityManager.createNativeQuery("DELETE FROM grant_assignment_groups WHERE grant_id = :grantId")
                    .setParameter("grantId", grantId).executeUpdate();
            } catch (Exception e2) {
                log.warn("Could not delete from grant_assignment_groups: {}", e2.getMessage());
            }
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM grant_negated_users WHERE grant_assignment_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
            try {
                entityManager.createNativeQuery("DELETE FROM grant_negated_users WHERE grant_id = :grantId")
                    .setParameter("grantId", grantId).executeUpdate();
            } catch (Exception e2) {
                log.warn("Could not delete from grant_negated_users: {}", e2.getMessage());
            }
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM grant_negated_groups WHERE grant_assignment_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
            try {
                entityManager.createNativeQuery("DELETE FROM grant_negated_groups WHERE grant_id = :grantId")
                    .setParameter("grantId", grantId).executeUpdate();
            } catch (Exception e2) {
                log.warn("Could not delete from grant_negated_groups: {}", e2.getMessage());
            }
        }
        
        // Elimina dalle relazioni con permissions - QUESTI DEVONO ESSERE ELIMINATI PRIMA!
        // I nomi delle tabelle sono generati da Hibernate senza underscore
        try {
            entityManager.createNativeQuery("DELETE FROM workerpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM statusownerpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM fieldownerpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM creatorpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM executorpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM fieldstatuspermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
        }
        
        // IMPORTANTE: Elimina i riferimenti da permission_assignment PRIMA di eliminare il Grant
        // per evitare violazioni di foreign key constraint
        try {
            entityManager.createNativeQuery("UPDATE permission_assignment SET grant_id = NULL WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
        } catch (Exception e) {
            log.warn("Could not remove grant reference from permission_assignment: {}", e.getMessage());
        }
        
        // Elimina la grant stessa
        entityManager.createNativeQuery("DELETE FROM grant_assignment WHERE id = :grantId")
            .setParameter("grantId", grantId).executeUpdate();
        
    }
}

