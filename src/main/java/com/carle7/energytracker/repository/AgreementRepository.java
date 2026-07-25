package com.carle7.energytracker.repository;

import com.carle7.energytracker.model.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, Long> {
}
