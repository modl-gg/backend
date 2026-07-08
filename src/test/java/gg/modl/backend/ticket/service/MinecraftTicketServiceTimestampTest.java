package gg.modl.backend.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class MinecraftTicketServiceTimestampTest {

    private static final Date REPORT_TIME = Date.from(Instant.parse("2026-07-08T05:22:40Z"));

    @Test
    void reconstructsSameDayUtcTime() {
        Date result = MinecraftTicketService.reconstructTimestamp("5:22:37", REPORT_TIME);

        assertEquals(Instant.parse("2026-07-08T05:22:37Z"), result.toInstant());
    }

    @Test
    void rollsBackToPreviousUtcDayWhenTimeIsAfterReport() {
        Date result = MinecraftTicketService.reconstructTimestamp("23:59:50", REPORT_TIME);

        assertEquals(Instant.parse("2026-07-07T23:59:50Z"), result.toInstant());
    }

    @Test
    void fallsBackToReportTimeWhenUnparseable() {
        Date result = MinecraftTicketService.reconstructTimestamp("not-a-time", REPORT_TIME);

        assertEquals(REPORT_TIME, result);
    }
}
