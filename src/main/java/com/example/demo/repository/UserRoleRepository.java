package com.example.demo.repository;

import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // ========================================
    // QUERY PER TENANT-LEVEL ROLES
    // ========================================

    /**
     * Trova tutti i UserRole di un utente in una specifica tenant
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId")
    List<UserRole> findByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * Trova tutti i UserRole di un utente in una tenant con uno specifico scope
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.scope = :scope")
    List<UserRole> findByUserIdAndTenantIdAndScope(@Param("userId") Long userId, 
                                                     @Param("tenantId") Long tenantId, 
                                                     @Param("scope") ScopeType scope);

    /**
     * Trova tutti i UserRole TENANT-level di un utente in una tenant
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.scope = 'TENANT' AND ur.project IS NULL")
    List<UserRole> findTenantRolesByUserAndTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * Trova tutte le tenant a cui un utente ha accesso (basandosi sui UserRole)
     */
    @Query("SELECT DISTINCT ur.tenant FROM UserRole ur WHERE ur.user.id = :userId AND ur.scope = 'TENANT'")
    List<Tenant> findTenantsByUserId(@Param("userId") Long userId);

    /**
     * Verifica se un utente ha un determinato ruolo in una tenant
     */
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
           "WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.roleName = :roleName AND ur.scope = 'TENANT'")
    boolean existsByUserIdAndTenantIdAndRoleName(@Param("userId") Long userId, 
                                                   @Param("tenantId") Long tenantId, 
                                                   @Param("roleName") String roleName);

    /**
     * Conta quanti utenti hanno un determinato ruolo in una tenant
     */
    @Query("SELECT COUNT(DISTINCT ur.user.id) FROM UserRole ur " +
           "WHERE ur.tenant.id = :tenantId AND ur.roleName = :roleName AND ur.scope = 'TENANT'")
    long countByTenantIdAndRoleName(@Param("tenantId") Long tenantId, @Param("roleName") String roleName);

    /**
     * Trova tutti gli utenti con un determinato ruolo in una tenant
     */
    @Query("SELECT DISTINCT ur.user FROM UserRole ur " +
           "WHERE ur.tenant.id = :tenantId AND ur.scope = 'TENANT'")
    List<User> findUsersByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Trova tutti gli utenti con un determinato ruolo specifico in una tenant
     */
    @Query("SELECT DISTINCT ur.user FROM UserRole ur " +
           "WHERE ur.tenant.id = :tenantId AND ur.roleName = :roleName AND ur.scope = 'TENANT'")
    List<User> findUsersByTenantIdAndRoleName(@Param("tenantId") Long tenantId, @Param("roleName") String roleName);

    /**
     * Elimina tutti i UserRole TENANT-level di un utente in una tenant
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.scope = 'TENANT'")
    void deleteByUserIdAndTenantIdAndScope(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * Trova un UserRole specifico
     */
    Optional<UserRole> findByUserAndTenantAndRoleNameAndScopeAndProject(
            User user, Tenant tenant, String roleName, ScopeType scope, Project project);

    // ========================================
    // QUERY PER PROJECT-LEVEL ROLES
    // ========================================

    /**
     * Trova tutti i UserRole PROJECT-level di un utente in un progetto
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId AND ur.project.id = :projectId AND ur.scope = 'PROJECT'")
    List<UserRole> findProjectRolesByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * Verifica se un utente ha un determinato ruolo in un progetto
     */
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
           "WHERE ur.user.id = :userId AND ur.project.id = :projectId AND ur.roleName = :roleName AND ur.scope = 'PROJECT'")
    boolean existsByUserIdAndProjectIdAndRoleName(@Param("userId") Long userId, 
                                                    @Param("projectId") Long projectId, 
                                                    @Param("roleName") String roleName);

    /**
     * Trova tutti i progetti dove un utente ha un certo ruolo
     */
    @Query("SELECT DISTINCT ur.project FROM UserRole ur " +
           "WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.roleName = :roleName AND ur.scope = 'PROJECT'")
    List<Project> findProjectsByUserTenantAndRole(@Param("userId") Long userId, 
                                                    @Param("tenantId") Long tenantId, 
                                                    @Param("roleName") String roleName);

    /**
     * Elimina tutti i UserRole PROJECT-level di un utente in un progetto
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.project.id = :projectId AND ur.scope = 'PROJECT'")
    void deleteByUserIdAndProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);

    // ========================================
    // QUERY GENERICHE
    // ========================================

    /**
     * Verifica se un utente ha accesso a una tenant (qualsiasi ruolo TENANT)
     */
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
           "WHERE ur.user.id = :userId AND ur.tenant.id = :tenantId AND ur.scope = 'TENANT'")
    boolean hasAccessToTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * Verifica se un utente ha accesso a un progetto (qualsiasi ruolo PROJECT)
     */
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
           "WHERE ur.user.id = :userId AND ur.project.id = :projectId AND ur.scope = 'PROJECT'")
    boolean hasAccessToProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}


