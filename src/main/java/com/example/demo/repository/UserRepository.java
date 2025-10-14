package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository per User.
 * Nota: Username è sempre un'email valida nel sistema.
 * Le associazioni User-Tenant sono ora gestite tramite UserRoleRepository.
 */
public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsername(String username);
}