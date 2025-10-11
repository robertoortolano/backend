package com.example.demo.repository;

import com.example.demo.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrantRepository extends JpaRepository<Grant, Long> {

    // Trova tutti i grant che includono esplicitamente un utente
    List<Grant> findByUsersContaining(User user);

    // Trova tutti i grant che negano esplicitamente un utente
    List<Grant> findByNegatedUsersContaining(User user);

    // Trova tutti i grant che includono esplicitamente un gruppo
    List<Grant> findByGroupsContaining(Group group);

    // Trova tutti i grant che negano esplicitamente un gruppo
    List<Grant> findByNegatedGroupsContaining(Group group);
    
    // Trova una grant con eager fetching di tutte le collezioni per evitare lazy loading
    @Query("SELECT g FROM Grant g " +
           "LEFT JOIN FETCH g.users " +
           "LEFT JOIN FETCH g.groups " +
           "LEFT JOIN FETCH g.negatedUsers " +
           "LEFT JOIN FETCH g.negatedGroups " +
           "WHERE g.id = :id")
    Optional<Grant> findByIdWithCollections(@Param("id") Long id);
    
    // Elimina una grant bypassando completamente Hibernate ORM
    @Modifying
    @Query(value = "DELETE FROM grant_assignment WHERE id = :grantId", nativeQuery = true)
    void deleteGrantNative(@Param("grantId") Long grantId);

}
