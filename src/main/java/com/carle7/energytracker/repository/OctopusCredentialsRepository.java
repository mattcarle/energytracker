package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.OctopusCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OctopusCredentialsRepository extends JpaRepository<OctopusCredentials, Long> {

    Optional<OctopusCredentials> findFirstByOrderByIdAsc();
}
