package com.example.demo.repository;

import com.example.demo.entity.Group;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByTenant(Tenant tenant);
    Optional<Group> findByIdAndTenant(Long id, Tenant tenant);
    Optional<Group> findByNameAndTenant(String name, Tenant tenant);
    boolean existsByNameAndTenant(String name, Tenant tenant);
}

