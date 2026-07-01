package com.firma.eventsync.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NormalizationUtil {

    private NormalizationUtil() {
    }

    private static final DateTimeFormatter LOCAL_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]");

    private static final List<DateTimeFormatter> CRM_DATE_FALLBACKS = List.of(
            DateTimeFormatter.ofPattern("MM-dd/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "a", "an", "of", "for", "with", "to", "in", "on", "at",
            "room", "office", "floor", "main", "virtual", "call", "meeting",
            "discussion", "review", "session", "update", "q1", "q2"
    );

    public static final class ParsedTime {
        public final LocalDate date;
        public final LocalDateTime dateTime;
        public final String note;

        public ParsedTime(LocalDate date, LocalDateTime dateTime, String note) {
            this.date = date;
            this.dateTime = dateTime;
            this.note = note;
        }

        public boolean hasTime() {
            return dateTime != null;
        }
    }

    public static ParsedTime parseCrm(String dateStr, String timeStr) {
        if (isBlank(dateStr)) {
            return new ParsedTime(null, null, "CRM record has no meeting_date");
        }

        String raw = dateStr.trim();
        LocalDate date = null;
        String note = null;

        try {
            date = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            for (DateTimeFormatter fmt : CRM_DATE_FALLBACKS) {
                try {
                    date = LocalDate.parse(raw, fmt);
                    note = "Repaired malformed CRM date '" + raw + "' -> " + date;
                    break;
                } catch (Exception ignore) {
                }
            }
        }

        if (date == null) {
            return new ParsedTime(null, null, "Unparseable CRM date '" + raw + "'");
        }

        if (isBlank(timeStr)) {
            return new ParsedTime(date, null, note);
        }

        try {
            LocalTime time = LocalTime.parse(timeStr.trim(),
                    DateTimeFormatter.ofPattern("H:mm"));
            return new ParsedTime(date, LocalDateTime.of(date, time), note);
        } catch (Exception e) {
            String timeNote = "Unparseable CRM time '" + timeStr + "'";
            return new ParsedTime(date, null, join(note, timeNote));
        }
    }

    public static ParsedTime parseCalendar(String startStr, ZoneId localZone) {
        if (isBlank(startStr)) {
            return new ParsedTime(null, null, "Calendar record has no start_time");
        }

        String raw = startStr.trim();
        boolean hasZone = raw.endsWith("Z") || hasExplicitOffset(raw);

        try {
            if (hasZone) {
                Instant instant = Instant.parse(raw);
                LocalDateTime local = LocalDateTime.ofInstant(instant, localZone);
                String note = "Calendar time '" + raw + "' was UTC; converted to "
                        + localZone.getId() + " -> " + local;
                return new ParsedTime(local.toLocalDate(), local, note);
            }
            LocalDateTime local = LocalDateTime.parse(raw, LOCAL_DT);
            return new ParsedTime(local.toLocalDate(), local, null);
        } catch (Exception e) {
            return new ParsedTime(null, null, "Unparseable calendar start_time '" + raw + "'");
        }
    }

    private static boolean hasExplicitOffset(String s) {
        int t = s.indexOf('T');
        if (t < 0) {
            return false;
        }
        String time = s.substring(t);
        return time.contains("+") || time.lastIndexOf('-') > 0;
    }

    public static String fixEmail(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim()
                .replace("[at]", "@")
                .replace("(at)", "@")
                .replace(" at ", "@");
    }

    public static boolean looksLikeEmail(String s) {
        return s != null && s.contains("@") && s.indexOf('@') < s.lastIndexOf('.');
    }

    public static String emailLocalPart(String email) {
        if (!looksLikeEmail(email)) {
            return null;
        }
        return email.substring(0, email.indexOf('@')).toLowerCase();
    }

    public static String emailDomainCore(String email) {
        if (!looksLikeEmail(email)) {
            return null;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        String[] labels = domain.split("\\.");
        if (labels.length >= 2) {
            return labels[labels.length - 2];
        }
        return labels.length > 0 ? labels[0] : null;
    }

    public static String companyCore(String company) {
        if (company == null) {
            return null;
        }
        return company.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static int commonPrefixLength(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    public static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        String[] parts = text.toLowerCase().split("[^a-z0-9]+");
        for (String p : parts) {
            if (p.length() >= 2 && !STOPWORDS.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public static double tokenSimilarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new LinkedHashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new LinkedHashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    public static List<String> nameTokens(String name) {
        if (name == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String p : name.toLowerCase().split("[^a-z]+")) {
            if (p.length() >= 2) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static List<String> splitWords(String s) {
        if (s == null) {
            return List.of();
        }
        return Arrays.asList(s.toLowerCase().split("[^a-z0-9]+"));
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a + "; " + b;
    }
}
