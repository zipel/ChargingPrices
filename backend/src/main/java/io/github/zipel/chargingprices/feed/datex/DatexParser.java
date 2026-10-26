package io.github.zipel.chargingprices.feed.datex;

import static io.github.zipel.chargingprices.feed.datex.Json.enumValue;
import static io.github.zipel.chargingprices.feed.datex.Json.multilingual;
import static io.github.zipel.chargingprices.feed.datex.Json.number;
import static io.github.zipel.chargingprices.feed.datex.Json.text;

import io.github.zipel.chargingprices.domain.Availability;
import io.github.zipel.chargingprices.domain.ChargePoint;
import io.github.zipel.chargingprices.domain.ChargePoint.Connector;
import io.github.zipel.chargingprices.domain.ChargePoint.CurrentType;
import io.github.zipel.chargingprices.domain.Site;
import io.github.zipel.chargingprices.domain.Tariff;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Maps DATEX II v3 "AFIR Energy Infrastructure" publications to the app's own model.
 *
 * <p>A feed has two parts. The table publication holds the static data (sites, stations, charge points,
 * connectors) and the status publication holds the dynamic data (availability and current prices).
 *
 * <p>Publishers fill in the standard differently, so several fields are read from more than one place.
 * Spirii puts the location in {@code locationReference}, the name in {@code name} and the operator in
 * {@code operator}; Eco-Movement uses {@code entrance}, {@code brand} and {@code energyDistributor}, keeps
 * EVSE IDs on the connectors and prices under {@code energyProduct}.
 */
@Component
public class DatexParser {

    /** What a status publication says about one charge point. */
    public record StatusUpdate(Availability availability, Tariff tariff, Instant updated) {
    }

    public List<Site> parseTable(JsonNode root, String source) {
        List<Site> sites = new ArrayList<>();
        for (JsonNode payload : payloads(root)) {
            JsonNode publication = payload.path("aegiEnergyInfrastructureTablePublication");
            for (JsonNode table : publication.path("energyInfrastructureTable")) {
                for (JsonNode site : table.path("energyInfrastructureSite")) {
                    Site parsed = parseSite(site, source);
                    if (parsed != null) {
                        sites.add(parsed);
                    }
                }
            }
        }
        return sites;
    }

    /** Status updates keyed by the charge point's feed ID ({@link ChargePoint#id()}). */
    public Map<String, StatusUpdate> parseStatuses(JsonNode root) {
        Map<String, StatusUpdate> updates = new HashMap<>();
        for (JsonNode payload : payloads(root)) {
            JsonNode publication = payload.path("aegiEnergyInfrastructureStatusPublication");
            for (JsonNode site : publication.path("energyInfrastructureSiteStatus")) {
                for (JsonNode station : site.path("energyInfrastructureStationStatus")) {
                    for (JsonNode refillPoint : station.path("refillPointStatus")) {
                        JsonNode point = refillPoint.path("aegiElectricChargingPointStatus");
                        String id = text(point.path("reference").path("idG"));
                        if (id == null) {
                            continue;
                        }
                        Tariff tariff = null;
                        for (JsonNode rateUpdate : point.path("energyRateUpdate")) {
                            tariff = TariffParser.parse(rateUpdate.path("energyPrice"), null);
                            break;
                        }
                        updates.put(id, new StatusUpdate(
                                Availability.fromDatex(enumValue(point.path("status"))),
                                tariff,
                                instant(text(point.path("lastUpdated")))));
                    }
                }
            }
        }
        return updates;
    }

    /**
     * Publications come as {@code {"payload": {...}}}, wrapped in a {@code messageContainer} with a list
     * (both Spirii), or directly at the top level (Eco-Movement).
     */
    private static List<JsonNode> payloads(JsonNode root) {
        if (root.has("aegiEnergyInfrastructureTablePublication") || root.has("aegiEnergyInfrastructureStatusPublication")) {
            return List.of(root);
        }
        JsonNode payload = root.has("messageContainer") ? root.path("messageContainer").path("payload") : root.path("payload");
        List<JsonNode> result = new ArrayList<>();
        if (payload.isArray()) {
            payload.forEach(result::add);
        } else if (!payload.isMissingNode()) {
            result.add(payload);
        }
        return result;
    }

    private Site parseSite(JsonNode site, String source) {
        JsonNode coordinates = findInLocation(site, "coordinatesForDisplay");
        Double latitude = coordinates == null ? null : number(coordinates.path("latitude"));
        Double longitude = coordinates == null ? null : number(coordinates.path("longitude"));
        if (latitude == null || longitude == null) {
            return null;
        }

        JsonNode address = findInLocation(site, "address");
        String street = null;
        String postcode = null;
        String city = null;
        if (address != null) {
            postcode = text(address.path("postcode"));
            city = multilingual(address.path("city"));
            for (JsonNode line : address.path("addressLine")) {
                street = multilingual(line.path("text"));
                if (street != null) {
                    break;
                }
            }
        }

        List<ChargePoint> chargePoints = new ArrayList<>();
        for (JsonNode station : site.path("energyInfrastructureStation")) {
            for (JsonNode refillPoint : station.path("refillPoint")) {
                JsonNode point = refillPoint.path("aegiElectricChargingPoint");
                if (!point.isMissingNode()) {
                    chargePoints.add(parseChargePoint(point));
                }
            }
        }

        String name = multilingual(site.path("name"));
        return new Site(
                source + "-" + text(site.path("idG")),
                source,
                name != null ? name : multilingual(site.path("brand")),
                operator(site),
                latitude,
                longitude,
                street,
                postcode,
                city,
                site.path("operatingHours").has("afacOpenAllHours"),
                chargePoints);
    }

    /** The location sits in {@code locationReference} (Spirii) or in {@code entrance} (Eco-Movement). */
    private static JsonNode findInLocation(JsonNode site, String field) {
        JsonNode found = site.path("locationReference").findValue(field);
        return found != null ? found : site.path("entrance").findValue(field);
    }

    /** The operator, or failing that the energy distributor of the site or of its first station. */
    private static String operator(JsonNode site) {
        String operator = multilingual(site.path("operator").path("afacAnOrganisation").path("name"));
        if (operator == null) {
            operator = multilingual(site.path("energyDistributor").path("afacAnOrganisation").path("name"));
        }
        if (operator == null) {
            for (JsonNode station : site.path("energyInfrastructureStation")) {
                operator = multilingual(station.path("energyDistributor").path("afacAnOrganisation").path("name"));
                break;
            }
        }
        return operator;
    }

    private ChargePoint parseChargePoint(JsonNode point) {
        List<Connector> connectors = new ArrayList<>();
        Double maxPowerKw = null;
        for (JsonNode connector : point.path("connector")) {
            String type = enumValue(connector.path("connectorType"));
            Double watts = number(connector.path("maxPowerAtSocket"));
            Double kw = watts != null && watts > 0 ? watts / 1000 : null;
            connectors.add(new Connector(type, ConnectorLabels.label(type), kw));
            if (kw != null && (maxPowerKw == null || kw > maxPowerKw)) {
                maxPowerKw = kw;
            }
        }
        if (maxPowerKw == null) {
            // Some feeds only give the power for the whole charge point
            for (JsonNode power : point.path("availableChargingPower")) {
                Double watts = number(power);
                Double kw = watts != null && watts > 0 ? watts / 1000 : null;
                if (kw != null && (maxPowerKw == null || kw > maxPowerKw)) {
                    maxPowerKw = kw;
                }
            }
        }

        return new ChargePoint(
                text(point.path("idG")),
                evseId(point),
                currentType(enumValue(point.path("currentType"))),
                connectors,
                maxPowerKw,
                Availability.UNKNOWN,
                staticTariff(point),
                null);
    }

    /** The EVSE ID sits on the charge point (Spirii) or on its connectors (Eco-Movement). */
    private static String evseId(JsonNode point) {
        String fromPoint = evseIdIn(point.path("externalIdentifier"));
        if (fromPoint != null) {
            return fromPoint;
        }
        for (JsonNode connector : point.path("connector")) {
            String fromConnector = evseIdIn(connector.path("externalIdentifier"));
            if (fromConnector != null) {
                return fromConnector;
            }
        }
        return null;
    }

    private static String evseIdIn(JsonNode identifiers) {
        String first = null;
        for (JsonNode identifier : identifiers) {
            String value = text(identifier.path("identifier"));
            if ("evseId".equals(text(identifier.path("typeOfIdentifier").path("extendedValueG")))) {
                return value;
            }
            if (first == null) {
                first = value;
            }
        }
        return first;
    }

    /**
     * The ad hoc rate from the static data. Status updates replace it with fresher prices later.
     * Rates sit in {@code electricEnergy} (Spirii) or in {@code energyProduct[].aegiElectricEnergy} (Eco-Movement).
     */
    private static Tariff staticTariff(JsonNode point) {
        List<JsonNode> energies = new ArrayList<>();
        point.path("electricEnergy").forEach(energies::add);
        point.path("energyProduct").forEach(product -> energies.add(product.path("aegiElectricEnergy")));
        for (JsonNode electricEnergy : energies) {
            for (JsonNode rate : electricEnergy.path("energyRate")) {
                String policy = enumValue(rate.path("ratePolicy"));
                if (policy == null || "adHoc".equals(policy)) {
                    String currency = null;
                    for (JsonNode c : rate.path("applicableCurrency")) {
                        currency = text(c);
                        break;
                    }
                    return TariffParser.parse(rate.path("energyPrice"), currency);
                }
            }
        }
        return null;
    }

    private static CurrentType currentType(String value) {
        if ("ac".equalsIgnoreCase(value)) {
            return CurrentType.AC;
        }
        return "dc".equalsIgnoreCase(value) ? CurrentType.DC : CurrentType.UNKNOWN;
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
