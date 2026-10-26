package io.github.zipel.chargingprices;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application with the feeds switched off, so the test never calls the internet. The empty
 * token also overrides a real one from a local .env file.
 */
@SpringBootTest(properties = {"feeds.spirii.enabled=false", "feeds.eco-movement.token="})
class ChargingPricesApplicationTests {

	@Test
	void contextLoads() {
	}

}
