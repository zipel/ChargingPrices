package io.github.zipel.chargingprices.places;

/**
 * A search result: a town, street or address in Denmark.
 *
 * @param bbox west, south, east, north, so the map can zoom to fit the whole place (null for a single point)
 */
public record Place(String name, String detail, double lat, double lon, double[] bbox) {
}
