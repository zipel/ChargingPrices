package io.github.zipel.chargingprices.places;

import java.util.List;

/** Something that can turn a search text into places. Lets tests replace the real geocoder. */
@FunctionalInterface
public interface PlaceLookup {

    List<Place> search(String query);
}
