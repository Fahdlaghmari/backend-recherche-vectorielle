package com.example.chatbotrag.model;

import jakarta.persistence.*;

@Entity
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;

    // 🔹 Constructeur vide (obligatoire pour JPA)
    public AdminUser() {
    }

    // 🔹 Constructeur complet
    public AdminUser(Long id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    // 🔹 Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
