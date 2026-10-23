package io.github.zipel.chargingprices.places;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceController {

    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    /** Towns, streets and addresses in Denmark matching the text. */
    @GetMapping("/api/places")
    public List<Place> search(@RequestParam("q") String query) {
        return places.search(query);
    }
}
