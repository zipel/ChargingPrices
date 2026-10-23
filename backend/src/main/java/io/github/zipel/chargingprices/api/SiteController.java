package io.github.zipel.chargingprices.api;

import io.github.zipel.chargingprices.api.SiteViews.Meta;
import io.github.zipel.chargingprices.api.SiteViews.SiteDetail;
import io.github.zipel.chargingprices.api.SiteViews.SitesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SiteController {

    private final SiteQueryService queries;

    public SiteController(SiteQueryService queries) {
        this.queries = queries;
    }

    /** All sites with their current prices, for the map. */
    @GetMapping("/sites")
    public SitesResponse sites() {
        return queries.sites();
    }

    /** One site with its charge points and price curves. */
    @GetMapping("/sites/{id}")
    public ResponseEntity<SiteDetail> site(@PathVariable String id) {
        return ResponseEntity.of(queries.site(id));
    }

    /** Data sources, operators and how fresh the data is. */
    @GetMapping("/meta")
    public Meta meta() {
        return queries.meta();
    }
}
