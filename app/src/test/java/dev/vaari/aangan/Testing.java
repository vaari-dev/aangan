package dev.vaari.aangan;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Shared test helpers. */
final class Testing {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private Testing() {
    }

    /** Fixes "now" at a moment in India, e.g. "2026-10-25T14:30". */
    static void now(String isoLocal) {
        Dates.fixed = Clock.fixed(LocalDateTime.parse(isoLocal).atZone(INDIA).toInstant(), INDIA);
    }

    static void resetClock() {
        Dates.fixed = null;
    }
}
