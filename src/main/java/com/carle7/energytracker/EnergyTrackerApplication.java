package com.carle7.energytracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.TimeZone;

@SpringBootApplication
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

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
