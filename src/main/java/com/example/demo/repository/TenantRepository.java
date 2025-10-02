package com.example.demo.repository;

import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

	boolean existsBySubdomain(String subdomain);

	Optional<Tenant> findBySubdomain(String subdomain); // utile per il redirect

	Optional<Tenant> findFirstByTenantAdminUsername(String username);

}
