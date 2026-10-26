package io.github.zipel.chargingprices.feed.datex;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zipel.chargingprices.domain.Tariff;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TariffParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void convertsEuroPricesToKroner() {
        // Mercedes-Benz High-Power Charging publishes 0.49 EUR + 20% VAT: 0.49 × 1.20 × 7.46038 = 4.39 kr
        Tariff tariff = TariffParser.parse(json("""
                [{"priceType": {"value": "pricePerKWh"}, "value": 0.49, "taxIncluded": false, "taxRate": 0.2}]
                """), "EUR");

        assertThat(tariff.fallbackPerKwh()).isEqualByComparingTo("4.39");
        assertThat(tariff.currency()).isEqualTo("DKK");
    }

    @Test
    void readsVatAsPercentOrFraction() {
        String percent = """
                [{"priceType": {"value": "pricePerKWh"}, "value": 2.79, "taxIncluded": false, "taxRate": 25}]
                """;
        String fraction = """
                [{"priceType": {"value": "pricePerKWh"}, "value": 2.79, "taxIncluded": false, "taxRate": 0.25}]
                """;

        assertThat(TariffParser.parse(json(percent), "DKK").fallbackPerKwh()).isEqualByComparingTo("3.49");
        assertThat(TariffParser.parse(json(fraction), "DKK").fallbackPerKwh()).isEqualByComparingTo("3.49");
    }

    @Test
    void givesNoTariffForUnexpectedCurrencies() {
        assertThat(TariffParser.parse(json("""
                [{"priceType": {"value": "pricePerKWh"}, "value": 4.5, "taxIncluded": true}]
                """), "SEK")).isNull();
    }

    @Test
    void statusUpdatesTakeTheirCurrencyFromTheReferencedRate() {
        // As in Eco-Movement's feed: the update has no currency, the table rate it points to does
        JsonNode document = json("""
                {"aegiEnergyInfrastructureTablePublication": {"energyInfrastructureTable": [{"energyInfrastructureSite": [
                   {"idG": "MB-1", "energyInfrastructureStation": [{"refillPoint": [{"aegiElectricChargingPoint": {
                     "idG": "DK*MBX*E1", "energyProduct": [{"aegiElectricEnergy": {"energyRate": [
                       {"idG": "rate-eur", "applicableCurrency": ["EUR"],
                        "energyPrice": [{"priceType": {"value": "pricePerKWh"}, "value": 0.49, "taxIncluded": false, "taxRate": 0.2}]}]}}]}}]}]}]}]},
                 "aegiEnergyInfrastructureStatusPublication": {"energyInfrastructureSiteStatus": [{"energyInfrastructureStationStatus": [
                   {"refillPointStatus": [{"aegiElectricChargingPointStatus": {
                     "reference": {"idG": "DK*MBX*E1"}, "status": {"value": "available"},
                     "energyRateUpdate": [{"energyRateReference": {"idG": "rate-eur"},
                       "energyPrice": [{"priceType": {"value": "pricePerKWh"}, "value": 0.49, "taxIncluded": false, "taxRate": 0.2}]}]}}]}]}]}}
                """);

        Tariff tariff = new DatexParser().parseStatuses(document).get("DK*MBX*E1").tariff();

        assertThat(tariff.priceAt(LocalDateTime.now())).hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("4.39"));
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
