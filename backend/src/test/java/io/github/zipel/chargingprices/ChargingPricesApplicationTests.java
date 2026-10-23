package io.github.zipel.chargingprices;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the whole application with the feeds switched off, so the test never calls the internet. */
@SpringBootTest(properties = "feeds.spirii.enabled=false")
class ChargingPricesApplicationTests {

	@Test
	void contextLoads() {
	}

}
