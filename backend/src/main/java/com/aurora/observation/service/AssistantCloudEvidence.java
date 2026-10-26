package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Restricts assistant weather evidence to one place and one requested local window. */
final class AssistantCloudEvidence {
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<!\\d)(\\d{1,2})月(\\d{1,2})[日号]?");
    private static final Pattern CURRENT = Pattern.compile("(?i)\\b(?:now|currently)\\b|现在|目前|当前");
    private static final Pattern RELATIVE = Pattern.compile(
            "(?i)tonight|tomorrow|yesterday|last night|day after tomorrow|now|currently|right now|今[天晚夜]|明[天晚夜]|后[天晚夜]|昨[天晚夜]|前晚|现在|目前|当前");
    record Window(Location location, String status, LocalDate localDate, Instant startUtc, Instant endUtc,
                  List<WeatherCloudPoint> points, String source, String sourceUrl) {}

    static String requestTime(String message, List<AssistantMessage> history) {
        if (hasTime(message)) return message;
        for (int i = history.size() - 1; i >= 0; i--) {
            var item = history.get(i);
            if ("user".equals(item.role()) && hasTime(item.content())) return item.content();
        }
        return "";
    }

    private static boolean hasTime(String value) {
        return value != null && (ISO_DATE.matcher(value).find() || MONTH_DAY.matcher(value).find() || RELATIVE.matcher(value).find());
    }

    static LocalDate requestedDate(String text, LocalDate today) {
        var dates = ISO_DATE.matcher(text);
        LocalDate explicit = null;
        while (dates.find()) {
            LocalDate candidate;
            try { candidate = LocalDate.parse(dates.group(1)); }
            catch (DateTimeException invalid) { return null; }
            if (explicit != null && !explicit.equals(candidate)) return null;
            explicit = candidate;
        }
        var monthDays = MONTH_DAY.matcher(text);
        while (monthDays.find()) {
            LocalDate candidate;
            try { candidate = LocalDate.of(today.getYear(), Integer.parseInt(monthDays.group(1)), Integer.parseInt(monthDays.group(2))); }
            catch (DateTimeException invalid) { return null; }
            if (explicit != null && !explicit.equals(candidate)) return null;
            explicit = candidate;
        }
        if (explicit != null) return explicit;
        String lower = text.toLowerCase(Locale.ROOT);
        if (CURRENT.matcher(lower).find()) return null;
        Set<Integer> offsets = new HashSet<>();
        if (lower.matches("(?s).*(day after tomorrow|后天|后晚|后夜).*")) {
            offsets.add(2); lower = lower.replace("day after tomorrow", "");
        }
        if (lower.matches("(?s).*(tomorrow|明天|明晚|明夜).*")) offsets.add(1);
        if (lower.matches("(?s).*(tonight|今天|今晚|今夜).*")) offsets.add(0);
        if (lower.matches("(?s).*(yesterday|last night|昨天|昨晚|昨夜).*")) offsets.add(-1);
        if (lower.contains("前晚")) offsets.add(-2);
        return offsets.size() == 1 ? today.plusDays(offsets.iterator().next()) : null;
    }

    static Window select(ObservationFactsResponse facts, String requestTime, LocalDate toolDate) {
        Location place = facts.outlook().location();
        ZoneId zone = ZoneId.of(place.timezone());
        Instant now = facts.generatedAtUtc();
        LocalDate requested = requestedDate(requestTime, now.atZone(zone).toLocalDate());
        LocalDate date = toolDate == null ? requested : toolDate;
        if (toolDate != null && requested != null && !toolDate.equals(requested)) {
            throw new IllegalArgumentException("Use the user's requested local date: " + requested);
        }
        // A night is grouped by local noon to next noon, including its following morning.
        // This calendar window does not assert darkness or aurora visibility.
        Instant start = date == null ? now : date.atTime(12, 0).atZone(zone).toInstant();
        Instant end = date == null ? now.plus(Duration.ofHours(3)) : date.plusDays(1).atTime(12, 0).atZone(zone).toInstant();
        var cloud = facts.cloudForecast();
        String status = "AVAILABLE";
        if (date != null && (facts.outlook().nights() == null || facts.outlook().nights().stream().noneMatch(n -> date.equals(n.localDate())))) {
            status = "OUTSIDE_FORECAST_WINDOW";
        } else if (cloud == null || (cloud.status() != FactFetchStatus.CURRENT && cloud.status() != FactFetchStatus.PARTIAL)
                || cloud.data() == null || cloud.data().expiresAt() == null || !cloud.data().expiresAt().isAfter(now)) {
            status = "UNAVAILABLE";
        } else if (date == null && !requestTime.isBlank() && !CURRENT.matcher(requestTime).find()) {
            status = "DATE_SELECTION_REQUIRED";
        }
        List<WeatherCloudPoint> points = !"AVAILABLE".equals(status) || cloud.data().cloudForecast() == null ? List.of()
                : cloud.data().cloudForecast().stream().filter(p -> p != null && p.validAt() != null
                    && p.cloudCoverPercent() != null && Double.isFinite(p.cloudCoverPercent())
                    && p.cloudCoverPercent() >= 0 && p.cloudCoverPercent() <= 100
                    && !p.validAt().isBefore(start) && p.validAt().isBefore(end) && !p.validAt().isBefore(now))
                    .sorted(Comparator.comparing(WeatherCloudPoint::validAt)).toList();
        if ("AVAILABLE".equals(status) && points.isEmpty()) status = "NO_DATA";
        return new Window(place, status, date, start, end, points,
                cloud == null ? null : cloud.source(), cloud == null ? null : cloud.sourceUrl());
    }

    static List<Map<String, Object>> localSamples(Window window) {
        ZoneId zone = ZoneId.of(window.location().timezone());
        return window.points().stream().map(p -> Map.<String, Object>of(
                "validAtUtc", p.validAt(), "localTime", p.validAt().atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "cloudCoverPercent", p.cloudCoverPercent())).toList();
    }
}
