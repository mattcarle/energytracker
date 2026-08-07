package com.carle7.energytracker.service;

import com.carle7.energytracker.model.OctopusCredentials;
import com.carle7.energytracker.repository.OctopusCredentialsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OctopusCredentialsService {

    private final OctopusCredentialsRepository octopusCredentialsRepository;

    public OctopusCredentialsService(OctopusCredentialsRepository octopusCredentialsRepository) {
        this.octopusCredentialsRepository = octopusCredentialsRepository;
    }

    public boolean hasCredentials() {
        return octopusCredentialsRepository.count() > 0;
    }

    public OctopusCredentials getCredentials() {
        return octopusCredentialsRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Octopus Energy API credentials have not been configured"));
    }

    @Transactional
    public OctopusCredentials saveCredentials(String accountNumber, String authToken) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Octopus account number is required");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("Octopus API auth token is required");
        }
        OctopusCredentials credentials = octopusCredentialsRepository.findFirstByOrderByIdAsc()
                .orElseGet(OctopusCredentials::new);
        credentials.setAccountNumber(accountNumber);
        credentials.setAuthToken(authToken);
        credentials.setUpdatedAt(LocalDateTime.now());
        return octopusCredentialsRepository.save(credentials);
    }
}
