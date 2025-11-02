package com.example.demo.repository;

import com.example.demo.entity.Project;
import com.example.demo.entity.UserFavoriteProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteProjectRepository extends JpaRepository<UserFavoriteProject, Long> {

    /**
     * Trova tutti i progetti preferiti di un utente in una tenant
     */
    @Query("SELECT ufp.project FROM UserFavoriteProject ufp " +
           "WHERE ufp.user.id = :userId AND ufp.tenant.id = :tenantId " +
           "ORDER BY ufp.addedAt DESC")
    List<Project> findFavoriteProjectsByUserAndTenant(@Param("userId") Long userId, 
                                                        @Param("tenantId") Long tenantId);

    /**
     * Verifica se un progetto è nei preferiti di un utente
     */
    @Query("SELECT CASE WHEN COUNT(ufp) > 0 THEN true ELSE false END FROM UserFavoriteProject ufp " +
           "WHERE ufp.user.id = :userId AND ufp.project.id = :projectId AND ufp.tenant.id = :tenantId")
    boolean existsByUserAndProjectAndTenant(@Param("userId") Long userId, 
                                             @Param("projectId") Long projectId, 
                                             @Param("tenantId") Long tenantId);

    /**
     * Trova il record specifico di preferito
     */
    Optional<UserFavoriteProject> findByUserIdAndProjectIdAndTenantId(Long userId, Long projectId, Long tenantId);

    /**
     * Elimina un preferito
     */
    void deleteByUserIdAndProjectIdAndTenantId(Long userId, Long projectId, Long tenantId);
}



























