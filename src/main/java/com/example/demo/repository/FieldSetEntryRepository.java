package com.example.demo.repository;

import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldSetEntryRepository extends JpaRepository<FieldSetEntry, Long> {

    List<FieldSetEntry> findByFieldSet_IdOrderByOrderIndex(Long fieldSetId);

    boolean existsByFieldSet_IdAndFieldConfiguration_Id(Long fieldSetId, Long fieldConfigId);

    void deleteByFieldSet_IdAndFieldConfiguration_Id(Long fieldSetId, Long fieldConfigId);

    List<FieldSetEntry> findByFieldConfigurationId(Long fieldConfigurationId);
    boolean existsByFieldConfiguration_Field_Id(Long fieldId);

    @Query("""
    SELECT DISTINCT e.fieldSet
    FROM FieldSetEntry e
    WHERE e.fieldConfiguration.field.id = :fieldId
      AND e.fieldSet.tenant.id = :tenantId
""")
    List<FieldSet> findDistinctFieldSetsByFieldIdAndTenantId(
            @Param("fieldId") Long fieldId,
            @Param("tenantId") Long tenantId
    );


    boolean existsByFieldConfiguration_IdAndFieldSet_Tenant_Id(Long fieldConfigurationId, Long tenantId);

}
