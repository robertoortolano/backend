package com.example.demo.repository;

import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository per Tenant.
 * Nota: Le query relative all'accesso utente-tenant sono ora in UserRoleRepository.
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
	boolean existsBySubdomain(String subdomain);
	Optional<Tenant> findBySubdomain(String subdomain);
	boolean existsByLicenseKey(String licenseKey);
	Optional<Tenant> findByLicenseKey(String licenseKey);
}
