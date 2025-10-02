package com.example.demo.repository;

import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT p FROM Project p WHERE p.tenant = :tenant")
    List<Project> findAllByTenant(@Param("tenant") Tenant tenant);

    boolean existsByProjectKeyAndTenantId(String projectKey, Long tenantId);

    boolean existsByProjectKeyAndTenant(String projectKey, Tenant tenant);

    List<Project> findByTenant(Tenant tenant);

    @Query("""
    SELECT DISTINCT p
    FROM GrantRoleAssignment gra
    JOIN gra.grant g
    JOIN g.users u
    JOIN gra.project p
    WHERE u.id = :userId AND gra.role.name = :roleName
""")
    List<Project> findProjectsByUserAndRole(@Param("userId") Long userId,
                                            @Param("roleName") RoleName roleName);


    Optional<Project> findByProjectKeyAndTenant(String projectKey, Tenant tenant);

    Optional<Project> findByIdAndTenant(Long projectId, Tenant tenant);

    @Query("""
    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
    FROM Project p
    WHERE p.id IN :projectIds
      AND p.itemTypeSet = :itemTypeSet
      AND p.tenant.id = :tenantId
""")
    boolean existsByIdInAndItemTypeSetAndTenant(
            @Param("projectIds") List<Long> projectIds,
            @Param("itemTypeSet") ItemTypeSet itemTypeSet,
            @Param("tenantId") Long tenantId
    );



    @Query("""
    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
    FROM Project p
    JOIN p.itemTypeSet its
    JOIN its.itemTypeConfigurations itConfiguration
    WHERE p.id IN :projectIds
      AND itConfiguration.fieldSet = :fieldSet
      AND p.tenant.id = :tenantId
""")
    boolean existsByIdInAndFieldSetAndTenant(
            @Param("projectIds") List<Long> projectIds,
            @Param("fieldSet") FieldSet fieldSet,
            @Param("tenantId") Long tenantId
    );





}
