package io.github.zipel.chargingprices;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ChargingPricesApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChargingPricesApplication.class, args);
	}

	/** Injected wherever "now" matters, so tests can fix the time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
