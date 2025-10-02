package com.example.demo.repository;

import com.example.demo.entity.ItemType;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {
    Optional<ItemType> findByNameAndTenant(String itemTypeName, Tenant tenant);
    Optional<ItemType> findByIdAndTenant(Long itemTypeId, Tenant tenant);
    List<ItemType> findByTenant(Tenant tenant);
    ItemType findByTenantAndName(Tenant tenant, String name);
    boolean existsByNameAndTenant(String name, Tenant tenant);
    List<ItemType> findAllByTenantAndDefaultItemTypeTrue(Tenant tenant);
    void deleteByIdAndTenant(Long id, Tenant tenant);
}


