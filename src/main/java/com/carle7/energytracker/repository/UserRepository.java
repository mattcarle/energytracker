package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(User.Role role);

    List<User> findAllByOrderByUsernameAsc();
}
