package com.example.demo.repository;

import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
    boolean existsByNameAndTenant(String name, Tenant tenant);
    List<Status> findAllByTenant(Tenant tenant);
    Optional<Status> findByIdAndTenant(Long id, Tenant tenant);
    Optional<Status> findByTenantIdAndName(Long tenantId, String name);
}
