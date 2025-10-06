package com.example.demo.repository;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrantRoleAssignmentRepository extends JpaRepository<GrantRoleAssignment, Long> {

    // Verifica se l'utente ha un ruolo con scope GLOBAL su una tenant
    @Query("""
        SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
          AND ga.role.name = :roleName
          AND ga.role.scope = 'TENANT'
    """)
    boolean existsByUserAndTenantAndRoleGlobal(@Param("userId") Long userId,
                                               @Param("tenantId") Long tenantId,
                                               @Param("roleName") String roleName);

    // Verifica se l'utente ha un ruolo con scope PROJECT su un progetto
    @Query("""
        SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
          AND ga.project.id = :projectId
          AND ga.role.name = :roleName
          AND ga.role.scope = 'PROJECT'
    """)
    boolean existsByUserAndTenantAndProjectAndRoleProject(@Param("userId") Long userId,
                                                          @Param("tenantId") Long tenantId,
                                                          @Param("projectId") Long projectId,
                                                          @Param("roleName") String roleName);

    @Query("""
    SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END
    FROM GrantRoleAssignment ga
    JOIN ga.grant g
    JOIN g.users u
    WHERE u.id = :userId
      AND ga.tenant.id = :tenantId
      AND ga.project.id = :projectId
      AND ga.role.name IN :roleNames
      AND ga.role.scope = 'PROJECT'
""")
    boolean existsByUserAndTenantAndProjectAndRoleProjectIn(@Param("userId") Long userId,
                                                            @Param("tenantId") Long tenantId,
                                                            @Param("projectId") Long projectId,
                                                            @Param("roleNames") List<String> roleNames);


    // Restituisce tutte le assegnazioni di ruolo per un utente in una tenant
    @Query("""
        SELECT ga
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
    """)
    List<GrantRoleAssignment> findAllByUserAndTenant(@Param("userId") Long userId,
                                                     @Param("tenantId") Long tenantId);

    // Restituisce tutte le assegnazioni di ruolo per un utente in un progetto
    @Query("""
        SELECT ga
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.project.id = :projectId
          AND ga.tenant.id = :tenantId
    """)
    List<GrantRoleAssignment> findAllByUserAndProject(@Param("userId") Long userId,
                                                      @Param("tenantId") Long tenantId,
                                                      @Param("projectId") Long projectId);

    @Query("""
    SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END
    FROM GrantRoleAssignment ga
    JOIN ga.grant g
    JOIN g.users u
    WHERE u.id = :userId
      AND ga.tenant.id = :tenantId
      AND ga.project.id = :projectId
""")
    boolean existsByUserAndTenantAndProject(@Param("userId") Long userId,
                                            @Param("tenantId") Long tenantId,
                                            @Param("projectId") Long projectId);


    // Restituisce una specifica assegnazione (grant + tenant + progetto)
    Optional<GrantRoleAssignment> findByGrantAndTenantAndProject(Grant grant, Tenant tenant, Project project);

    // Restituisce i progetti dove un utente ha un certo ruolo con scope PROJECT
    @Query("""
        SELECT ga.project
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
          AND ga.role.name = :roleName
          AND ga.role.scope = 'PROJECT'
    """)
    List<Project> findProjectsByUserTenantAndRoleProject(@Param("userId") Long userId,
                                                         @Param("tenantId") Long tenantId,
                                                         @Param("roleName") String roleName);

    // Verifica se esiste almeno un GrantRoleAssignment per utente, tenant e ruolo con scope PROJECT
    @Query("""
        SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
          AND ga.role.name = :roleName
          AND ga.role.scope = 'PROJECT'
    """)
    boolean existsByUserAndTenantAndRoleProject(@Param("userId") Long userId,
                                                @Param("tenantId") Long tenantId,
                                                @Param("roleName") String roleName);

    // Restituisce solo gli ID dei progetti dove l’utente ha un certo ruolo PROJECT
    @Query("""
        SELECT DISTINCT ga.project.id
        FROM GrantRoleAssignment ga
        JOIN ga.grant g
        JOIN g.users u
        WHERE u.id = :userId
          AND ga.tenant.id = :tenantId
          AND ga.role.name = :roleName
          AND ga.role.scope = 'PROJECT'
          AND ga.project IS NOT NULL
    """)
    List<Long> findProjectIdsByUserAndTenantAndRole(@Param("userId") Long userId,
                                                    @Param("tenantId") Long tenantId,
                                                    @Param("roleName") String roleName);

    @Query("""
    SELECT ga
    FROM GrantRoleAssignment ga
    WHERE ga.role.name = :roleName
      AND ga.role.scope = :scope
      AND ga.tenant = :tenant
""")
    Optional<GrantRoleAssignment> findFirstByStringAndScopeAndTenant(
            @Param("roleName") String roleName,
            @Param("scope") ScopeType scope,
            @Param("tenant") Tenant tenant);


}
