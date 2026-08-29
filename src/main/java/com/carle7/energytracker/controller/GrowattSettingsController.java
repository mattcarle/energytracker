package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.ErrorResponse;
import com.carle7.energytracker.dto.GrowattCredentialsRequest;
import com.carle7.energytracker.service.GrowattCredentialsService;
import com.carle7.energytracker.service.GrowattService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/growatt")
public class GrowattSettingsController {

    private final GrowattCredentialsService growattCredentialsService;
    private final GrowattService growattService;

    public GrowattSettingsController(GrowattCredentialsService growattCredentialsService, GrowattService growattService) {
        this.growattCredentialsService = growattCredentialsService;
        this.growattService = growattService;
    }

    @GetMapping("/credentials/status")
    public CredentialsStatusResponse credentialsStatus() {
        if (!growattCredentialsService.hasCredentials()) {
            return new CredentialsStatusResponse(false, null);
        }
        return new CredentialsStatusResponse(true, growattCredentialsService.getCredentials().getPlantId());
    }

    // Saves the token, then immediately resolves/verifies the plant against the real API, so a
    // bad token is reported back straight away rather than only surfacing later when a scheduled
    // load silently fails - same "fail fast at setup time" spirit as AuthController#setup.
    @PostMapping("/credentials")
    public ResponseEntity<?> saveCredentials(@RequestBody GrowattCredentialsRequest request) {
        try {
            growattCredentialsService.saveToken(request.getApiToken());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }

        GrowattService.PlantLoadResult plantLoad = growattService.loadPlant();
        if (plantLoad.getError() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(plantLoad.getError()));
        }
        return ResponseEntity.ok(plantLoad);
    }

    // Always 200: the result carries its own error field, matching DataLoadController's
    // account/usage load endpoints - a partial failure still reports whatever succeeded.
    @PostMapping("/load")
    public ResponseEntity<GrowattService.SolarLoadResult> loadSolarData(
            @RequestParam(defaultValue = "false") boolean deleteAll) {
        return ResponseEntity.ok(growattService.loadSolarData(deleteAll));
    }

    public static class CredentialsStatusResponse {
        private final boolean configured;
        private final String plantId;

        public CredentialsStatusResponse(boolean configured, String plantId) {
            this.configured = configured;
            this.plantId = plantId;
        }

        public boolean isConfigured() {
            return configured;
        }

        public String getPlantId() {
            return plantId;
        }
    }
}
