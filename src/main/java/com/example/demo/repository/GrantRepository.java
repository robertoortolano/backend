package com.example.demo.repository;

import com.example.demo.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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

}
