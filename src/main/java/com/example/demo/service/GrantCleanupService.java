package com.example.demo.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GrantCleanupService {
    
    private final EntityManager entityManager;
    
    /**
     * Elimina una grant con una transazione completamente separata
     * per evitare conflitti con Hibernate Session
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteGrantCompletely(Long grantId) {
        if (grantId == null) {
            return;
        }
        
        System.out.println("DEBUG: GrantCleanupService - Deleting grant in separate transaction, grantId=" + grantId);
        
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
                System.out.println("WARN: Could not delete from grant_assignment_users: " + e2.getMessage());
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
                System.out.println("WARN: Could not delete from grant_assignment_groups: " + e2.getMessage());
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
                System.out.println("WARN: Could not delete from grant_negated_users: " + e2.getMessage());
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
                System.out.println("WARN: Could not delete from grant_negated_groups: " + e2.getMessage());
            }
        }
        
        // Elimina dalle relazioni con permissions - QUESTI DEVONO ESSERE ELIMINATI PRIMA!
        // I nomi delle tabelle sono generati da Hibernate senza underscore
        try {
            entityManager.createNativeQuery("DELETE FROM workerpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from workerpermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from workerpermission_grant: " + e.getMessage());
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM statusownerpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from statusownerpermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from statusownerpermission_grant: " + e.getMessage());
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM fieldeditorpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from fieldeditorpermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from fieldeditorpermission_grant: " + e.getMessage());
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM creatorpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from creatorpermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from creatorpermission_grant: " + e.getMessage());
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM executorpermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from executorpermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from executorpermission_grant: " + e.getMessage());
        }
        
        try {
            entityManager.createNativeQuery("DELETE FROM fieldstatuspermission_grant WHERE grant_id = :grantId")
                .setParameter("grantId", grantId).executeUpdate();
            System.out.println("DEBUG: Deleted from fieldstatuspermission_grant");
        } catch (Exception e) {
            System.out.println("WARN: Could not delete from fieldstatuspermission_grant: " + e.getMessage());
        }
        
        // Elimina la grant stessa
        entityManager.createNativeQuery("DELETE FROM grant_assignment WHERE id = :grantId")
            .setParameter("grantId", grantId).executeUpdate();
        
        System.out.println("DEBUG: GrantCleanupService - Grant deleted successfully");
    }
}

