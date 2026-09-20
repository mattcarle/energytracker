package com.carle7.energytracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EnergyTrackerApplication {

	static {
		// All persisted timestamps are UTC-intended naive LocalDateTimes. Without this, the JVM's
		// default zone (e.g. Europe/London) leaks into JDBC timestamp handling and can collapse two
		// distinct UTC instants that fall in a local DST "spring forward" gap into the same stored value.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(EnergyTrackerApplication.class, args);
	}

	// Both Octopus and Growatt API calls share this bean (see OctopusApiService/GrowattApiService).
	// Without explicit timeouts, a stalled/unresponsive call blocks its caller forever - and since
	// the daily @Scheduled jobs run on a small fixed thread pool (see
	// spring.task.scheduling.pool.size), one indefinitely-hung call can permanently starve every
	// later firing of every scheduled job, not just the one that hung.
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder
				.connectTimeout(Duration.ofSeconds(10))
				.readTimeout(Duration.ofSeconds(30))
				.build();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
