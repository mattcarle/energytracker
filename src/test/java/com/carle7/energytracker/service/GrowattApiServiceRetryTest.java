package com.carle7.energytracker.service;

import com.carle7.energytracker.config.GrowattConfig;
import com.carle7.energytracker.model.GrowattCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Growatt sometimes closes the connection partway through a response (seen live on mix_data):
//   RestClientException: Error while extracting response for type [String] ...
//     Caused by IOException: closed <- IOException: chunked transfer encoding, state:
//     READING_LENGTH <- EOFException: EOF reached while reading
// That's transient and a retry usually works, so it is retried per request; timeouts and
// non-I/O failures are not.
@ExtendWith(MockitoExtension.class)
class GrowattApiServiceRetryTest {

    private static final String MIX_DATA_BODY =
            "{\"error_msg\":\"\",\"data\":{\"count\":1,\"datas\":[{\"time\":\"2026-09-21 10:00:00\",\"ppv\":1500.0}]},\"error_code\":0}";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GrowattConfig growattConfig;

    @Mock
    private GrowattCredentialsService growattCredentialsService;

    @InjectMocks
    private GrowattApiService growattApiService;

    // lenient: shared setup, and the pure-function test below uses none of it.
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(growattApiService, "growattObjectMapper",
                GrowattApiService.configureGrowattObjectMapper(new ObjectMapper()));
        // No real pauses in a unit test.
        ReflectionTestUtils.setField(growattApiService, "retryBackoffMs", new long[] {0, 0});
        lenient().when(growattConfig.getBaseUrl()).thenReturn("https://growatt.test/v1");
        GrowattCredentials credentials = mock(GrowattCredentials.class);
        lenient().when(credentials.getApiToken()).thenReturn("token");
        lenient().when(growattCredentialsService.getCredentials()).thenReturn(credentials);
    }

    // The exact chain from the live stack trace.
    private static RestClientException truncatedResponse() {
        EOFException eof = new EOFException("EOF reached while reading");
        IOException chunked = new IOException("chunked transfer encoding, state: READING_LENGTH", eof);
        IOException closed = new IOException("closed", chunked);
        return new RestClientException("Error while extracting response for type [class java.lang.String] and content type [text/plain;charset=UTF-8]", closed);
    }

    private GrowattApiService.MixDataResult fetch() {
        return growattApiService.fetchMixData("SN123", LocalDate.of(2026, 9, 21));
    }

    @Test
    void aResponseCutOffOnceIsRetriedAndTheSecondAttemptIsUsed() {
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenThrow(truncatedResponse())
                .thenReturn(ResponseEntity.ok(MIX_DATA_BODY));

        GrowattApiService.MixDataResult result = fetch();

        assertThat(result.error).isNull();
        assertThat(result.points).hasSize(1);
        assertThat(result.points.get(0).ppv).isEqualTo(1500.0);
        verify(restTemplate, times(2)).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void aResponseCutOffEveryTimeGivesUpAfterThreeAttemptsWithAReadableError() {
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class))).thenThrow(truncatedResponse());

        GrowattApiService.MixDataResult result = fetch();

        assertThat(result.points).isNull();
        assertThat(result.error).contains("complete response from Growatt").contains("3 times");
        verify(restTemplate, times(3)).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void aReadTimeoutIsNotRetried() {
        // Against a hung server three attempts would triple the 30 s read timeout.
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        GrowattApiService.MixDataResult result = fetch();

        assertThat(result.points).isNull();
        verify(restTemplate, times(1)).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void aGrowattErrorResponseIsNotRetried() {
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"error_msg\":\"error_frequently_access\",\"error_code\":10012}"));

        GrowattApiService.MixDataResult result = fetch();

        assertThat(result.error).isEqualTo("error_frequently_access");
        verify(restTemplate, times(1)).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void pagesAreRetriedIndividuallyNotTheWholeFetch() {
        // Page 1 is full (100 points) so page 2 is requested; page 2's first attempt is cut off.
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            points.append(i > 0 ? "," : "").append("{\"time\":\"2026-09-21 10:00:00\",\"ppv\":1.0}");
        }
        String fullPage = "{\"error_msg\":\"\",\"data\":{\"count\":101,\"datas\":[" + points + "]},\"error_code\":0}";
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(fullPage))
                .thenThrow(truncatedResponse())
                .thenReturn(ResponseEntity.ok(MIX_DATA_BODY));

        GrowattApiService.MixDataResult result = fetch();

        assertThat(result.error).isNull();
        assertThat(result.points).hasSize(101);
        // page 1 once, page 2 twice - page 1 is not fetched again.
        verify(restTemplate, times(3)).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void onlyNonTimeoutIoFailuresCountAsTransient() {
        assertThat(GrowattApiService.isTransientIoFailure(truncatedResponse())).isTrue();
        assertThat(GrowattApiService.isTransientIoFailure(
                new ResourceAccessException("I/O error", new java.net.ConnectException("Network is unreachable")))).isTrue();
        assertThat(GrowattApiService.isTransientIoFailure(
                new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")))).isFalse();
        assertThat(GrowattApiService.isTransientIoFailure(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")))).isFalse();
        assertThat(GrowattApiService.isTransientIoFailure(new IllegalStateException("bug"))).isFalse();
    }
}
