package com.example.demo.security;

import com.example.demo.entity.GrantRoleAssignment;
import com.example.demo.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;


@Getter
public class CustomUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient User user;
    private final transient List<GrantRoleAssignment> assignments;

    public CustomUserDetails(User user, List<GrantRoleAssignment> assignments) {
        this.user = user;
        this.assignments = assignments;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return assignments.stream()
                .map(ga -> "ROLE_" + ga.getRole().getName().name())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }


    @Override
    public String getPassword() {
        return user.getPasswordHash(); // assicurati che sia cifrata
    }

    @Override
    public String getUsername() {
        return user.getUsername(); // o username se preferisci
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public Long getTenantId() {
        return user.getActiveTenant().getId();
    }
}
