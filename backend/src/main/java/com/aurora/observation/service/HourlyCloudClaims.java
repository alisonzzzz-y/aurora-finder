package com.aurora.observation.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts local-hour cloud percentages from an assistant answer so they can be checked against the source. */
final class HourlyCloudClaims {
    private static final Pattern TIME = Pattern.compile("(?<![\\d:.])((?:[01]?\\d|2[0-3]))(?::([0-5]\\d)|[时点])");
    // UTC offsets such as UTC+13:00, GMT-5, +05:45 or (−03:00) are not local hours.
    private static final Pattern UTC_OFFSET = Pattern.compile(
            "(?i)\\b(?:UTC|GMT)\\s*[+\\-−±]\\s*\\d{1,2}(?::?\\d{2})?|[+]\\d{1,2}:\\d{2}|\\(\\s*[−-]\\d{1,2}:\\d{2}\\s*\\)");
    private static final Pattern TIME_JOIN = Pattern.compile("(?i)\\s*(?:-|–|—|~|～|to|until|through|至|到)\\s*");
    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*[%％]");
    private static final Pattern PERCENT_RANGE = Pattern.compile(
            "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*[%％]?\\s*(?:-|–|—|~|～|to|and|至|到|和)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[%％]");
    private static final int MAX_DISTANCE = 60;

    /** A single local time, or an inclusive local time range, with the percentages stated for it. */
    record Claim(int start, String fromHour, String toHour, List<BigDecimal> values) {
        boolean isRange() { return toHour != null; }
    }

    private HourlyCloudClaims() {}

    static List<Claim> parse(String answer) {
        String text = maskUtcOffsets(answer);
        List<MatchResult> times = TIME.matcher(text).results().toList();
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            MatchResult first = times.get(i);
            MatchResult last = first;
            if (i + 1 < times.size()
                    && TIME_JOIN.matcher(text.substring(first.end(), times.get(i + 1).start())).matches()) {
                last = times.get(++i);
            }
            // Read values only until the next time, so a value is never attributed to an earlier hour.
            int segmentEnd = Math.min(text.length(), last.end() + MAX_DISTANCE);
            if (i + 1 < times.size()) segmentEnd = Math.min(segmentEnd, times.get(i + 1).start());
            int newline = text.indexOf('\n', last.end());
            if (newline >= 0) segmentEnd = Math.min(segmentEnd, newline);
            List<BigDecimal> values = values(text.substring(last.end(), segmentEnd));
            if (values.isEmpty()) continue;
            claims.add(new Claim(first.start(), hour(first), last == first ? null : hour(last), values));
        }
        return claims;
    }

    private static List<BigDecimal> values(String segment) {
        Matcher single = PERCENT.matcher(segment);
        if (!single.find()) return List.of();
        Matcher range = PERCENT_RANGE.matcher(segment);
        if (range.find() && range.start() <= single.start()) {
            return List.of(new BigDecimal(range.group(1)), new BigDecimal(range.group(2)));
        }
        return List.of(new BigDecimal(single.group(1)));
    }

    private static String maskUtcOffsets(String answer) {
        StringBuilder masked = new StringBuilder(answer);
        Matcher offsets = UTC_OFFSET.matcher(answer);
        while (offsets.find()) {
            for (int index = offsets.start(); index < offsets.end(); index++) masked.setCharAt(index, ' ');
        }
        return masked.toString();
    }

    private static String hour(MatchResult time) {
        return String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(time.group(1)),
                time.group(2) == null ? "00" : time.group(2));
    }
}
