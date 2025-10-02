// FieldRepository.java
package com.example.demo.repository;

import com.example.demo.entity.Field;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldRepository extends JpaRepository<Field, Long> {
    List<Field> findAllByTenant(Tenant tenant);
    Optional<Field> findByTenantAndName(Tenant tenant, String name);
    Optional<Field> findByIdAndTenant(Long id, Tenant tenant);
    List<Field> findByDefaultFieldTrueAndTenant(Tenant tenant);
}
