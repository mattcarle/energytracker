package com.carle7.energytracker.service;

import com.carle7.energytracker.model.User;
import com.carle7.energytracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private static final String ADMIN_USERNAME = "admin";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isSetupRequired() {
        return userRepository.countByRole(User.Role.ADMIN) == 0;
    }

    @Transactional
    public User setupAdmin(String password) {
        if (!isSetupRequired()) {
            throw new IllegalStateException("Admin account has already been set up");
        }
        validatePassword(password);
        User admin = new User(ADMIN_USERNAME, passwordEncoder.encode(password), User.Role.ADMIN, false);
        return userRepository.save(admin);
    }

    @Transactional
    public User createUser(String username, String initialPassword, User.Role role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validatePassword(initialPassword);
        User user = new User(username, passwordEncoder.encode(initialPassword), role != null ? role : User.Role.USER, true);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id, User currentUser) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (target.getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        if (target.getRole() == User.Role.ADMIN && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("Cannot delete the last admin account");
        }
        userRepository.delete(target);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }
}
