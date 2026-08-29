package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.GrowattCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrowattCredentialsRepository extends JpaRepository<GrowattCredentials, Long> {

    Optional<GrowattCredentials> findFirstByOrderByIdAsc();
}
