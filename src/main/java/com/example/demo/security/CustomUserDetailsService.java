package com.example.demo.security;

import com.example.demo.entity.GrantRoleAssignment;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.entity.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                    GrantRoleAssignmentRepository grantRoleAssignmentRepository) {
        this.userRepository = userRepository;
        this.grantRoleAssignmentRepository = grantRoleAssignmentRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<GrantRoleAssignment> assignments = Collections.emptyList();
        if (user.getActiveTenant() != null) {
            assignments = grantRoleAssignmentRepository.findAllByUserAndTenant(
                    user.getId(), user.getActiveTenant().getId());
        }

        return new CustomUserDetails(user, assignments);
    }

}

