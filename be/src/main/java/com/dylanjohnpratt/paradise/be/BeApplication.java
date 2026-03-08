package com.dylanjohnpratt.paradise.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application entry point for the Paradise backend.
 * Enables scheduled task execution (daily task resets, cache warming, recurring notification
 * processing) and automatic scanning of {@code @ConfigurationProperties} records for
 * type-safe configuration binding.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeApplication.class, args);
	}

}
