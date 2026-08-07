package com.carle7.energytracker.dto;

import com.carle7.energytracker.model.User;

import java.time.LocalDateTime;

public class UserResponse {

    private final Long id;
    private final String username;
    private final User.Role role;
    private final boolean mustChangePassword;
    private final LocalDateTime createdAt;

    public UserResponse(Long id, String username, User.Role role, boolean mustChangePassword, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword(), user.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public User.Role getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
