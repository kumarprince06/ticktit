package com.ticktit.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * GST invoice numbers: TT/2026-27/000123, no gaps within a financial year (April to March, IST).
 * The counter row is locked for the rest of the confirming transaction, so a rollback leaves no gap either.
 */
@Component
@RequiredArgsConstructor
public class InvoiceNumbers {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        String year = financialYear(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        jdbc.update("INSERT INTO invoice_counters (financial_year) VALUES (?) ON CONFLICT DO NOTHING", year);
        long no = jdbc.queryForObject("SELECT next_no FROM invoice_counters WHERE financial_year = ? FOR UPDATE", Long.class, year);
        jdbc.update("UPDATE invoice_counters SET next_no = next_no + 1 WHERE financial_year = ?", year);
        return "TT/%s/%06d".formatted(year, no);
    }

    /** 2026-11-13 -> "2026-27"; 2027-02-01 -> "2026-27"; 2027-04-01 -> "2027-28". */
    static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return "%d-%02d".formatted(start, (start + 1) % 100);
    }
}
