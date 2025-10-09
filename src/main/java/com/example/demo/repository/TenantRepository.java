package com.example.demo.repository;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
	List<Tenant> findByUsersContaining(User user);
	boolean existsBySubdomain(String subdomain);
	Optional<Tenant> findBySubdomain(String subdomain);
	boolean existsByLicenseKey(String licenseKey);
	Optional<Tenant> findByLicenseKey(String licenseKey);
	
	@Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tenant t JOIN t.users u WHERE t.id = :tenantId AND u.id = :userId")
	boolean existsByIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
