package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.UpdateProfileRequest;
import com.carle7.energytracker.dto.UserResponse;
import com.carle7.energytracker.model.User;
import com.carle7.energytracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/api/users/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/api/users/me")
    public ResponseEntity<?> updateCurrentUser(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        if (request.email() != null && !request.email().isBlank() && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "email is already registered"));
            }
            user.setEmail(request.email());
        }

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "current password is incorrect"));
            }
            if (request.newPassword().length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
            }
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
