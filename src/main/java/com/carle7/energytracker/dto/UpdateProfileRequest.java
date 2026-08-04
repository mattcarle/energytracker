package com.carle7.energytracker.dto;

public record UpdateProfileRequest(String email, String currentPassword, String newPassword) {
}
