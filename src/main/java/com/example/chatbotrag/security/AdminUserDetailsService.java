package com.example.chatbotrag.security;

import com.example.chatbotrag.model.AdminUser;
import com.example.chatbotrag.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin non trouvé : " + username));

        return new User(
                admin.getUsername(),
                admin.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
