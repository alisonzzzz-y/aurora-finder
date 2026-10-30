package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Restricts assistant weather evidence to one place and one requested local window. */
final class AssistantCloudEvidence {
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<!\\d)(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?");
    private static final Pattern ENGLISH_DATE = Pattern.compile("(?i)\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern CURRENT = Pattern.compile("(?i)\\b(?:now|currently)\\b|现在|目前|当前");
    private static final Pattern AFTER_TOMORROW = Pattern.compile("(?i)\\bday\\s+after\\s+tomorrow\\b|后[天晚夜]");
    private static final Pattern BEFORE_YESTERDAY = Pattern.compile("(?i)\\bday\\s+before\\s+yesterday\\b|前[天晚夜]");
    private static final Pattern TOMORROW = Pattern.compile("(?i)\\btomorrow\\b|明[天晚夜]");
    private static final Pattern TODAY = Pattern.compile("(?i)\\b(?:today|tonight)\\b|今[天晚夜]");
    private static final Pattern YESTERDAY = Pattern.compile("(?i)\\b(?:yesterday|last\\s+night)\\b|昨[天晚夜]");
    private static final List<Pattern> RELATIVE = List.of(CURRENT, AFTER_TOMORROW, BEFORE_YESTERDAY, TOMORROW, TODAY, YESTERDAY);
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
        return value != null && (ISO_DATE.matcher(value).find() || MONTH_DAY.matcher(value).find() || ENGLISH_DATE.matcher(value).find() || RELATIVE.stream().anyMatch(pattern -> pattern.matcher(value).find()));
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
            try { candidate = LocalDate.of(monthDays.group(1) == null ? today.getYear() : Integer.parseInt(monthDays.group(1)), Integer.parseInt(monthDays.group(2)), Integer.parseInt(monthDays.group(3))); }
            catch (DateTimeException invalid) { return null; }
            if (explicit != null && !explicit.equals(candidate)) return null;
            explicit = candidate;
        }
        var englishDates = ENGLISH_DATE.matcher(text);
        while (englishDates.find()) {
            String name = englishDates.group(1).substring(0,3).toUpperCase(Locale.ROOT);
            int month = Arrays.stream(Month.values()).filter(m -> m.name().startsWith(name)).findFirst().orElseThrow().getValue();
            LocalDate candidate;
            try { candidate = LocalDate.of(englishDates.group(3) == null ? today.getYear() : Integer.parseInt(englishDates.group(3)), month, Integer.parseInt(englishDates.group(2))); }
            catch (DateTimeException invalid) { return null; }
            if (explicit != null && !explicit.equals(candidate)) return null;
            explicit = candidate;
        }
        if (explicit != null) return explicit;
        if (CURRENT.matcher(text).find()) return null;
        Set<Integer> offsets = new HashSet<>();
        // Remove longer phrases first so their embedded tomorrow/yesterday is not counted twice.
        String remaining = text;
        if (AFTER_TOMORROW.matcher(remaining).find()) {
            offsets.add(2);
            remaining = AFTER_TOMORROW.matcher(remaining).replaceAll(" ");
        }
        if (BEFORE_YESTERDAY.matcher(remaining).find()) {
            offsets.add(-2);
            remaining = BEFORE_YESTERDAY.matcher(remaining).replaceAll(" ");
        }
        if (TOMORROW.matcher(remaining).find()) offsets.add(1);
        if (TODAY.matcher(remaining).find()) offsets.add(0);
        if (YESTERDAY.matcher(remaining).find()) offsets.add(-1);
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
