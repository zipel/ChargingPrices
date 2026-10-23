package io.github.zipel.chargingprices.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real site names from the feed. */
class SiteAccessTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Taastrupgaard 17 (Privat)",
            "Ole Rømers Vej 4 - Privat parkering",
            "Private only – APM.Holding",
            "Mærsk employee only – Closed parking facility P1",
            "PenSam - C.F. Richs Vej (Kun for beboere)",
            "Herlev Boligselskab - Enebærstien (Kun beboere)",
            "TDC Group - Kun medarbejdere/firmabilister/lejere. Max. 3 timer",
            "Fælledby (Bag lukket hegn)",
            "Antons airfield NEW (2) ⛔"})
    void flagsSitesThatSayTheyArePrivate(String name) {
        assertThat(SiteAccess.looksRestricted(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Gildhøj Privathospital", "Nannasgade 28", "Kæmpestranden", "Nordhavnens P-hus (Tagparkering)"})
    void leavesOrdinarySitesAlone(String name) {
        assertThat(SiteAccess.looksRestricted(name)).isFalse();
    }
}
