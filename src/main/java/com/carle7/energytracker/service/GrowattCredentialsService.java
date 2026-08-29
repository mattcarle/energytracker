package com.carle7.energytracker.service;

import com.carle7.energytracker.model.GrowattCredentials;
import com.carle7.energytracker.repository.GrowattCredentialsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class GrowattCredentialsService {

    private final GrowattCredentialsRepository growattCredentialsRepository;

    public GrowattCredentialsService(GrowattCredentialsRepository growattCredentialsRepository) {
        this.growattCredentialsRepository = growattCredentialsRepository;
    }

    public boolean hasCredentials() {
        return growattCredentialsRepository.count() > 0;
    }

    public GrowattCredentials getCredentials() {
        return growattCredentialsRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Growatt API credentials have not been configured"));
    }

    @Transactional
    public GrowattCredentials saveToken(String apiToken) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("Growatt API token is required");
        }
        GrowattCredentials credentials = growattCredentialsRepository.findFirstByOrderByIdAsc()
                .orElseGet(GrowattCredentials::new);
        credentials.setApiToken(apiToken);
        credentials.setUpdatedAt(LocalDateTime.now());
        return growattCredentialsRepository.save(credentials);
    }

    @Transactional
    public GrowattCredentials savePlantDetails(String plantId, LocalDate installDate, String deviceSn) {
        GrowattCredentials credentials = getCredentials();
        credentials.setPlantId(plantId);
        credentials.setInstallDate(installDate);
        credentials.setDeviceSn(deviceSn);
        credentials.setUpdatedAt(LocalDateTime.now());
        return growattCredentialsRepository.save(credentials);
    }
}
