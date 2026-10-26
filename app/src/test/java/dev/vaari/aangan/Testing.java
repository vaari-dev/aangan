package dev.vaari.aangan;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class Testing {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private Testing() {
    }

    static void now(String isoLocal) {
        Dates.fixed = Clock.fixed(LocalDateTime.parse(isoLocal).atZone(INDIA).toInstant(), INDIA);
    }

    static void resetClock() {
        Dates.fixed = null;
    }
}
