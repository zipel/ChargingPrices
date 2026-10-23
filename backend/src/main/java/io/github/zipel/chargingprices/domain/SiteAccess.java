package io.github.zipel.chargingprices.domain;

import java.util.regex.Pattern;

/**
 * Guesses whether a site is closed to the public from its name.
 *
 * <p>The AFIR feeds also list semi-public chargers (housing associations, company car parks), and the only
 * hint is often the name, e.g. "Taastrupgaard 17 (Privat)" or "Kun for beboere". This is a heuristic, so the
 * UI hides these sites by default but lets people show them.
 */
public final class SiteAccess {

    private static final Pattern RESTRICTED = Pattern.compile(
            "(?iu)(\\bprivate?\\b"
                    + "|\\bkun\\s+(for\\s+)?(medarbejder|ansatte|beboer|gæster|kunder|personale|lejere)"
                    + "|employees?\\s+only"
                    + "|ikke\\s+offentlig"
                    + "|lukket\\s+(hegn|varegård|parkering|område)"
                    + "|⛔)");

    private SiteAccess() {
    }

    public static boolean looksRestricted(String siteName) {
        return siteName != null && RESTRICTED.matcher(siteName).find();
    }
}
