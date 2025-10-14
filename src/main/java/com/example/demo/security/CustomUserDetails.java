package com.example.demo.security;

import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
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
    private final transient List<UserRole> userRoles;

    public CustomUserDetails(User user, List<UserRole> userRoles) {
        this.user = user;
        this.userRoles = userRoles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return userRoles.stream()
                .map(ur -> {
                    // Genera authority nel formato ROLE_{SCOPE}_{ROLENAME}
                    // Es: ROLE_TENANT_ADMIN, ROLE_TENANT_USER, ROLE_PROJECT_ADMIN
                    String authority = "ROLE_" + ur.getScope().name() + "_" + ur.getRoleName();
                    return authority;
                })
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }


    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
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
        return user.getActiveTenant();
    }
}
