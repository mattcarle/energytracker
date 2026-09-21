package com.carle7.energytracker.controller;

import com.carle7.energytracker.repository.SolarGenerationRepository;
import com.carle7.energytracker.service.GrowattApiService.MixDataPointDto;
import com.carle7.energytracker.service.GrowattApiService.MixDataResult;
import com.carle7.energytracker.service.GrowattCredentialsService;
import com.carle7.energytracker.service.GrowattService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// The Live tab's chart is drawn from the same mix_data points the endpoint already fetched to find
// the latest reading, so the response carries the whole day's curve alongside it (rather than the
// page making a second, identical mix_data request via /api/solar/hourly).
@ExtendWith(MockitoExtension.class)
class SolarControllerLiveTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private SolarGenerationRepository solarGenerationRepository;

    @Mock
    private GrowattCredentialsService growattCredentialsService;

    @Mock
    private GrowattService growattService;

    @InjectMocks
    private SolarController solarController;

    private static MixDataPointDto point(String time, Double ppv) {
        MixDataPointDto dto = new MixDataPointDto();
        dto.time = time;
        dto.ppv = ppv;
        return dto;
    }

    @Test
    void responseCarriesTodaysWholeCurveOldestFirstAndTheLatestReadingAsTheHeadline() {
        when(growattService.today()).thenReturn(TODAY);
        // Growatt returns points in arbitrary order.
        when(growattService.getLivePowerCurve(TODAY)).thenReturn(new MixDataResult(List.of(
                point("2026-09-21 10:05:00", 2500.0),
                point("2026-09-21 09:55:00", 1800.0),
                point("2026-09-21 10:00:00", 2100.0)), null));

        SolarController.SolarLiveResponse response = solarController.getSolarLive();

        assertThat(response.getPoints()).extracting(SolarController.PowerPoint::getTime)
                .containsExactly("2026-09-21 09:55:00", "2026-09-21 10:00:00", "2026-09-21 10:05:00");
        assertThat(response.getPoints().get(2).getPowerWatts()).isEqualByComparingTo("2500");
        assertThat(response.getTime()).isEqualTo("2026-09-21 10:05:00");
        assertThat(response.getSolarWatts()).isEqualByComparingTo("2500");
        assertThat(response.getError()).isNull();
    }

    @Test
    void noReadingsYetGivesAnEmptyCurveRatherThanNull() {
        when(growattService.today()).thenReturn(TODAY);
        when(growattService.getLivePowerCurve(TODAY)).thenReturn(new MixDataResult(List.of(), null));

        SolarController.SolarLiveResponse response = solarController.getSolarLive();

        assertThat(response.getPoints()).isEmpty();
        assertThat(response.getTime()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void growattFailureGivesAnEmptyCurveAndTheError() {
        when(growattService.today()).thenReturn(TODAY);
        when(growattService.getLivePowerCurve(TODAY)).thenReturn(new MixDataResult(null, "Growatt API error 10011"));

        SolarController.SolarLiveResponse response = solarController.getSolarLive();

        assertThat(response.getPoints()).isEmpty();
        assertThat(response.getError()).isEqualTo("Growatt API error 10011");
    }
}
