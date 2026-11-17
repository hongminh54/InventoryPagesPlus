package me.cortezromeo.inventorypagesplus.history;

import me.cortezromeo.inventorypagesplus.Settings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

public final class HistoryDates {
    private HistoryDates() {
    }

    public static DateTimeFormatter formatter(String pattern) {
        if (pattern == null || !pattern.matches("[dMyu./_-]{8,16}") || !pattern.matches(".*[./_-].*")
                || !(pattern.contains("yyyy") || pattern.contains("uuuu")))
            throw new IllegalArgumentException("Use separated numeric day, month and four-digit year, without spaces or time fields.");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu"), Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        LocalDate sample = LocalDate.of(2024, 2, 29);
        if (!sample.equals(LocalDate.parse(formatter.format(sample), formatter)))
            throw new IllegalArgumentException("Date format must include day, month and year.");
        return formatter;
    }

    public static LocalDate date(long millis) {
        return Instant.ofEpochMilli(millis).atZone(Settings.HISTORY_ZONE).toLocalDate();
    }

    public static LocalDate parse(String value) {
        LocalDate date = LocalDate.parse(value, Settings.HISTORY_DATE_FORMATTER);
        if (date.getYear() < 1 || date.getYear() > 9999)
            throw new DateTimeParseException("Year must be between 0001 and 9999.", value, 0);
        return date;
    }

    public static String format(LocalDate date) {
        return Settings.HISTORY_DATE_FORMATTER.format(date);
    }

    public static long start(LocalDate date) {
        return date.atStartOfDay(Settings.HISTORY_ZONE).toInstant().toEpochMilli();
    }

    public static long cutoff(LocalDate today, int months, ZoneId zone) {
        return today.minusMonths(months).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static long cutoff() {
        return cutoff(LocalDate.now(Settings.HISTORY_ZONE), Settings.HISTORY_RETENTION_MONTHS, Settings.HISTORY_ZONE);
    }

    public static int page(String value) {
        int page = Integer.parseInt(value);
        if (page < 1)
            throw new NumberFormatException("Page must be positive.");
        return page - 1;
    }
}
