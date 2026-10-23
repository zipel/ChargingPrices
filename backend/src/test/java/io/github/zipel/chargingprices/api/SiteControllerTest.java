package io.github.zipel.chargingprices.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.github.zipel.chargingprices.api.SiteViews.Meta;
import io.github.zipel.chargingprices.api.SiteViews.SiteSummary;
import io.github.zipel.chargingprices.api.SiteViews.SitesResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Checks the HTTP layer only: paths, status codes and JSON field names. */
@WebMvcTest(SiteController.class)
class SiteControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T21:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SiteQueryService queries;

    @Test
    void listsSites() {
        SiteSummary site = new SiteSummary("spirii-site-1", "Nannasgade 28", "Spirii ApS", 55.70, 12.55,
                "Nannasgade 28", "København N", false, new BigDecimal("3.46"), 1, 2, List.of());
        given(queries.sites()).willReturn(new SitesResponse(NOW, List.of(site)));

        assertThat(mvc.get().uri("/api/sites"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.sites[0].priceNow").isEqualTo(3.46);
    }

    @Test
    void unknownSiteIs404() {
        given(queries.site("nope")).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/sites/nope")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void metaSaysWhetherDataIsStillLoading() {
        given(queries.meta()).willReturn(new Meta(NOW, true, 0, 0, List.of(), List.of()));

        assertThat(mvc.get().uri("/api/meta"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.loading").isEqualTo(true);
    }
}
