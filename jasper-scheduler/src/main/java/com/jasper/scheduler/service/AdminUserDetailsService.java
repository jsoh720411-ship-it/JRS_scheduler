package com.jasper.scheduler.service;

import com.jasper.scheduler.mapper.AdminUserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserMapper adminUserMapper;

    public AdminUserDetailsService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String adminId) throws UsernameNotFoundException {
        String passwordHash = adminUserMapper.findPasswordHashById(adminId);
        if (passwordHash == null) {
            throw new UsernameNotFoundException("Admin not found: " + adminId);
        }
        adminUserMapper.updateLastLogin(adminId);
        return new User(adminId, passwordHash,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
