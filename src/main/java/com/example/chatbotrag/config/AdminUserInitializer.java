package com.example.chatbotrag.config;

import com.example.chatbotrag.model.AdminUser;
import com.example.chatbotrag.repository.AdminUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AdminUserInitializer {

    @Bean
    public CommandLineRunner initAdmin(AdminUserRepository repository, BCryptPasswordEncoder encoder) {
        return args -> {
            if (repository.count() == 0) {
                AdminUser admin = new AdminUser();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                repository.save(admin);
                System.out.println("✅ Admin par défaut créé : admin / admin123");
            } else {
                System.out.println("ℹ️ Admin existant détecté. Aucun nouveau compte créé.");
            }
        };
    }
}
