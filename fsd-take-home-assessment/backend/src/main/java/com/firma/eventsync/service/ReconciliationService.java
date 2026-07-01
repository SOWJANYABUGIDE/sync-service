package com.firma.eventsync.service;

import com.firma.eventsync.ingest.DataLoader;
import com.firma.eventsync.model.CalendarEvent;
import com.firma.eventsync.model.Conflict;
import com.firma.eventsync.model.CrmEvent;
import com.firma.eventsync.model.FieldProvenance;
import com.firma.eventsync.model.MeetingSource;
import com.firma.eventsync.model.ReconciliationStats;
import com.firma.eventsync.model.UnifiedMeeting;
import com.firma.eventsync.service.NormalizationUtil.ParsedTime;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final long DUP_TIME_WINDOW_MINUTES = 90;
    private static final long START_TIME_CONFLICT_MINUTES = 15;

    private final DataLoader dataLoader;
    private final MatchScorer matchScorer;
    private final ZoneId localZone;
    private final int timeWindowMinutes;
    private final double minScore;

    private List<UnifiedMeeting> meetings = List.of();
    private Map<String, UnifiedMeeting> byId = Map.of();
    private ReconciliationStats stats = new ReconciliationStats();

    public ReconciliationService(DataLoader dataLoader,
                                 MatchScorer matchScorer,
                                 @Value("${event-sync.local-zone}") String localZone,
                                 @Value("${event-sync.match.time-window-minutes}") int timeWindowMinutes,
                                 @Value("${event-sync.match.min-score}") double minScore) {
        this.dataLoader = dataLoader;
        this.matchScorer = matchScorer;
        this.localZone = ZoneId.of(localZone);
        this.timeWindowMinutes = timeWindowMinutes;
        this.minScore = minScore;
    }

    @PostConstruct
    public void reconcile() {
        List<CrmEvent> crm = dataLoader.loadCrmEvents();
        List<CalendarEvent> cal = dataLoader.loadCalendarEvents();

        ParsedTime[] crmTimes = new ParsedTime[crm.size()];
        for (int i = 0; i < crm.size(); i++) {
            crmTimes[i] = NormalizationUtil.parseCrm(crm.get(i).meeting_date, crm.get(i).meeting_time);
        }
        ParsedTime[] calTimes = new ParsedTime[cal.size()];
        for (int j = 0; j < cal.size(); j++) {
            calTimes[j] = NormalizationUtil.parseCalendar(cal.get(j).start_time, localZone);
        }

        int nCrm = crm.size();
        int nCal = cal.size();
        UnionFind uf = new UnionFind(nCrm + nCal);

        for (int i = 0; i < nCrm; i++) {
            for (int j = 0; j < nCal; j++) {
                double s = matchScorer.score(crm.get(i), crmTimes[i], cal.get(j), calTimes[j], timeWindowMinutes);
                if (s >= minScore) {
                    uf.union(i, nCrm + j);
                }
            }
        }
        for (int a = 0; a < nCal; a++) {
            for (int b = a + 1; b < nCal; b++) {
                if (calendarDuplicate(cal.get(a), calTimes[a], cal.get(b), calTimes[b])) {
                    uf.union(nCrm + a, nCrm + b);
                }
            }
        }
        for (int a = 0; a < nCrm; a++) {
            for (int b = a + 1; b < nCrm; b++) {
                if (crmDuplicate(crm.get(a), crmTimes[a], crm.get(b), crmTimes[b])) {
                    uf.union(a, b);
                }
            }
        }

        Map<Integer, List<Integer>> components = new LinkedHashMap<>();
        for (int n = 0; n < nCrm + nCal; n++) {
            components.computeIfAbsent(uf.find(n), k -> new ArrayList<>()).add(n);
        }

        List<UnifiedMeeting> built = new ArrayList<>();
        int duplicatesCollapsed = 0;
        for (List<Integer> nodes : components.values()) {
            List<Integer> crmIdx = new ArrayList<>();
            List<Integer> calIdx = new ArrayList<>();
            for (int n : nodes) {
                if (n < nCrm) {
                    crmIdx.add(n);
                } else {
                    calIdx.add(n - nCrm);
                }
            }
            duplicatesCollapsed += Math.max(0, crmIdx.size() - 1) + Math.max(0, calIdx.size() - 1);
            built.add(buildMeeting(crm, crmTimes, cal, calTimes, crmIdx, calIdx));
        }

        built.sort(Comparator
                .comparing((UnifiedMeeting m) -> m.getStartTime() == null ? "9999" : m.getStartTime())
                .thenComparing(m -> m.getTitle() == null ? "" : m.getTitle()));

        Map<String, UnifiedMeeting> index = new LinkedHashMap<>();
        for (int i = 0; i < built.size(); i++) {
            String id = String.format("M-%03d", i + 1);
            built.get(i).setId(id);
            index.put(id, built.get(i));
        }

        this.meetings = built;
        this.byId = index;
        this.stats = computeStats(built, nCrm, nCal, duplicatesCollapsed);

        log.info("Reconciliation complete: {} unified meetings from {} CRM + {} calendar records "
                        + "({} matched, {} CRM-only, {} calendar-only, {} with conflicts, {} duplicates collapsed)",
                stats.getTotalMeetings(), nCrm, nCal, stats.getMatchedBothSources(),
                stats.getCrmOnly(), stats.getCalendarOnly(), stats.getMeetingsWithConflicts(),
                stats.getDuplicateRecordsCollapsed());
    }

    public List<UnifiedMeeting> getMeetings() {
        return meetings;
    }

    public Optional<UnifiedMeeting> getMeeting(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public ReconciliationStats getStats() {
        return stats;
    }

    private UnifiedMeeting buildMeeting(List<CrmEvent> crm, ParsedTime[] crmTimes,
                                        List<CalendarEvent> cal, ParsedTime[] calTimes,
                                        List<Integer> crmIdx, List<Integer> calIdx) {
        UnifiedMeeting m = new UnifiedMeeting();

        Integer crmI = crmIdx.isEmpty() ? null : crmIdx.get(0);
        CrmEvent crmRec = crmI == null ? null : crm.get(crmI);
        ParsedTime crmTime = crmI == null ? null : crmTimes[crmI];

        Integer calI = pickRepresentativeCalendar(calIdx, calTimes, crmTime);
        CalendarEvent calRec = calI == null ? null : cal.get(calI);
        ParsedTime calTime = calI == null ? null : calTimes[calI];

        if (crmRec != null) {
            m.getSources().add(MeetingSource.CRM);
            m.setCrmId(crmRec.crm_id);
        }
        for (int j : calIdx) {
            m.getCalendarIds().add(cal.get(j).event_id);
        }
        if (!calIdx.isEmpty()) {
            m.getSources().add(MeetingSource.CALENDAR);
        }

        m.setMatchScore(bestCrossScore(crm, crmTimes, cal, calTimes, crmIdx, calIdx));

        String crmSubject = crmRec == null ? null : crmRec.subject;
        String calTitle = calRec == null ? null : calRec.title;
        String title = calTitle != null ? calTitle : crmSubject;
        addField(m, "title", title, crmSubject, calTitle, false);
        m.setTitle(title);

        String crmStart = crmTime != null && crmTime.hasTime() ? crmTime.dateTime.toString() : null;
        String calStart = calTime != null && calTime.hasTime() ? calTime.dateTime.toString() : null;
        boolean startConflict = crmTime != null && calTime != null
                && crmTime.hasTime() && calTime.hasTime()
                && Math.abs(Duration.between(crmTime.dateTime, calTime.dateTime).toMinutes()) > START_TIME_CONFLICT_MINUTES;
        String primaryStart = resolveStart(crmTime, calTime);
        addField(m, "startTime", primaryStart, crmStart, calStart, startConflict);
        m.setStartTime(primaryStart);
        m.setDate(resolveDate(crmTime, calTime));

        if (calRec != null) {
            ParsedTime end = NormalizationUtil.parseCalendar(calRec.end_time, localZone);
            m.setEndTime(end.hasTime() ? end.dateTime.toString() : null);
        }

        String crmLoc = crmRec == null ? null : crmRec.location;
        String calLoc = calRec == null ? null : calRec.location;
        boolean locConflict = crmLoc != null && calLoc != null && !locationCompatible(crmLoc, calLoc);
        String location = crmLoc != null ? crmLoc : calLoc;
        addField(m, "location", location, crmLoc, calLoc, locConflict);
        m.setLocation(location);

        String crmType = crmRec == null ? null : crmRec.meeting_type;
        String calModalityLabel = calLoc == null ? null
                : ("virtual".equals(modalityBucket(calLoc)) ? "Virtual" : "In-Person");
        boolean modalityConflict = crmType != null && calModalityLabel != null
                && !modalityBucket(crmType).equals(modalityBucket(calLoc));
        String meetingType = crmType != null ? crmType : calModalityLabel;
        addField(m, "meetingType", meetingType, crmType, calModalityLabel, modalityConflict);
        m.setMeetingType(meetingType);

        String crmStatus = crmRec == null ? null : crmRec.status;
        String calStatus = calRec == null ? null : calRec.status;
        boolean statusConflict = crmStatus != null && calStatus != null
                && !statusBucket(crmStatus).equals(statusBucket(calStatus));
        String status = crmStatus != null ? crmStatus : calStatus;
        addField(m, "status", status, crmStatus, calStatus, statusConflict);
        m.setStatus(status);

        String owner = crmRec == null ? null : crmRec.relationship_owner;
        String organizer = calRec == null ? null : calRec.organizer;
        String primaryOrganizer = organizer != null ? organizer : owner;
        addField(m, "organizer", primaryOrganizer, owner, organizer, false);
        m.setOrganizer(primaryOrganizer);

        if (crmRec != null) {
            m.setClientName(crmRec.client_name);
            m.setClientCompany(crmRec.client_company);
        }

        List<String> attendees = repairedAttendees(calRec);
        addField(m, "participants", attendees,
                clientDisplay(crmRec), attendees.isEmpty() ? null : attendees, false);
        m.setParticipants(mergeParticipants(attendees, crmRec));

        String notes = crmRec == null ? null : emptyToNull(crmRec.notes);
        String description = calRec == null ? null : calRec.description;
        addField(m, "notes", notes != null ? notes : description, notes, description, false);

        if (calRec != null) {
            m.setRecurring(calRec.is_recurring);
        }

        collectDataQualityNotes(m, crm, crmTimes, cal, calTimes, crmIdx, calIdx, crmI, calI);

        return m;
    }

    private Integer pickRepresentativeCalendar(List<Integer> calIdx, ParsedTime[] calTimes, ParsedTime crmTime) {
        if (calIdx.isEmpty()) {
            return null;
        }
        if (crmTime != null && crmTime.hasTime()) {
            return calIdx.stream()
                    .filter(j -> calTimes[j].hasTime())
                    .min(Comparator.comparingLong(j ->
                            Math.abs(Duration.between(crmTime.dateTime, calTimes[j].dateTime).toMinutes())))
                    .orElse(calIdx.get(0));
        }
        return calIdx.stream()
                .filter(j -> calTimes[j].hasTime())
                .min(Comparator.comparing(j -> calTimes[j].dateTime))
                .orElse(calIdx.get(0));
    }

    private double bestCrossScore(List<CrmEvent> crm, ParsedTime[] crmTimes,
                                  List<CalendarEvent> cal, ParsedTime[] calTimes,
                                  List<Integer> crmIdx, List<Integer> calIdx) {
        double best = 0.0;
        for (int i : crmIdx) {
            for (int j : calIdx) {
                best = Math.max(best, matchScorer.score(crm.get(i), crmTimes[i], cal.get(j), calTimes[j], timeWindowMinutes));
            }
        }
        return Math.round(best * 100.0) / 100.0;
    }

    private void addField(UnifiedMeeting m, String field, Object primary,
                          Object crmValue, Object calValue, boolean conflict) {
        m.getFields().put(field, new FieldProvenance(field, primary, crmValue, calValue, conflict));
        if (conflict) {
            m.getConflicts().add(new Conflict(field, crmValue, calValue));
        }
    }

    private boolean calendarDuplicate(CalendarEvent a, ParsedTime ta, CalendarEvent b, ParsedTime tb) {
        if (ta.date == null || tb.date == null || !ta.date.equals(tb.date)) {
            return false;
        }
        if (ta.hasTime() && tb.hasTime()) {
            long delta = Math.abs(Duration.between(ta.dateTime, tb.dateTime).toMinutes());
            if (delta > DUP_TIME_WINDOW_MINUTES) {
                return false;
            }
        }
        return attendeesOverlap(a, b) || NormalizationUtil.tokenSimilarity(a.title, b.title) >= 0.4;
    }

    private boolean crmDuplicate(CrmEvent a, ParsedTime ta, CrmEvent b, ParsedTime tb) {
        if (ta.date == null || tb.date == null || !ta.date.equals(tb.date)) {
            return false;
        }
        if (ta.hasTime() && tb.hasTime()) {
            long delta = Math.abs(Duration.between(ta.dateTime, tb.dateTime).toMinutes());
            if (delta > DUP_TIME_WINDOW_MINUTES) {
                return false;
            }
        }
        String ca = NormalizationUtil.companyCore(a.client_company);
        String cb = NormalizationUtil.companyCore(b.client_company);
        boolean sameCompany = ca != null && ca.equals(cb);
        return sameCompany && NormalizationUtil.tokenSimilarity(a.subject, b.subject) >= 0.4;
    }

    private boolean attendeesOverlap(CalendarEvent a, CalendarEvent b) {
        if (a.attendees == null || b.attendees == null) {
            return false;
        }
        for (String x : a.attendees) {
            String dx = NormalizationUtil.emailDomainCore(NormalizationUtil.fixEmail(x));
            String lx = NormalizationUtil.emailLocalPart(NormalizationUtil.fixEmail(x));
            for (String y : b.attendees) {
                String fy = NormalizationUtil.fixEmail(y);
                if (lx != null && lx.equals(NormalizationUtil.emailLocalPart(fy)) && !isInternal(x)) {
                    return true;
                }
                String dy = NormalizationUtil.emailDomainCore(fy);
                if (dx != null && dx.equals(dy) && !"firma".equals(dx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInternal(String email) {
        return "firma".equals(NormalizationUtil.emailDomainCore(NormalizationUtil.fixEmail(email)));
    }

    private String modalityBucket(String text) {
        if (text == null) {
            return "unknown";
        }
        String l = text.toLowerCase();
        if (l.contains("zoom") || l.contains("teams") || l.contains("virtual")
                || l.contains("webex") || l.contains("meet") || l.contains("http")) {
            return "virtual";
        }
        return "physical";
    }

    private String statusBucket(String status) {
        if (status == null) {
            return "unknown";
        }
        String l = status.toLowerCase();
        if (l.contains("cancel")) {
            return "cancelled";
        }
        if (l.contains("tentative")) {
            return "tentative";
        }
        return "active";
    }

    private boolean locationCompatible(String a, String b) {
        String na = a.toLowerCase().replaceAll("[^a-z0-9]", "");
        String nb = b.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (na.isEmpty() || nb.isEmpty()) {
            return true;
        }
        if (na.contains(nb) || nb.contains(na)) {
            return true;
        }
        var ta = NormalizationUtil.tokens(a);
        var tb = NormalizationUtil.tokens(b);
        for (String t : ta) {
            if (t.length() >= 4 && tb.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private String resolveStart(ParsedTime crmTime, ParsedTime calTime) {
        if (calTime != null && calTime.hasTime()) {
            return calTime.dateTime.toString();
        }
        if (crmTime != null && crmTime.hasTime()) {
            return crmTime.dateTime.toString();
        }
        if (crmTime != null && crmTime.date != null) {
            return crmTime.date.atStartOfDay().toString();
        }
        if (calTime != null && calTime.date != null) {
            return calTime.date.atStartOfDay().toString();
        }
        return null;
    }

    private String resolveDate(ParsedTime crmTime, ParsedTime calTime) {
        if (crmTime != null && crmTime.date != null) {
            return crmTime.date.toString();
        }
        if (calTime != null && calTime.date != null) {
            return calTime.date.toString();
        }
        return null;
    }

    private List<String> repairedAttendees(CalendarEvent cal) {
        List<String> out = new ArrayList<>();
        if (cal == null || cal.attendees == null) {
            return out;
        }
        for (String a : cal.attendees) {
            if (!NormalizationUtil.isBlank(a)) {
                out.add(NormalizationUtil.fixEmail(a));
            }
        }
        return out;
    }

    private List<String> mergeParticipants(List<String> attendees, CrmEvent crm) {
        List<String> out = new ArrayList<>(attendees);
        String client = clientDisplay(crm);
        if (client != null && out.stream().noneMatch(a -> matchesClient(a, crm))) {
            out.add(client);
        }
        return out;
    }

    private boolean matchesClient(String email, CrmEvent crm) {
        if (crm == null) {
            return false;
        }
        List<String> nameTokens = NormalizationUtil.nameTokens(crm.client_name);
        String localPart = NormalizationUtil.emailLocalPart(email);
        if (localPart != null && !nameTokens.isEmpty()) {
            for (String t : nameTokens) {
                if (!localPart.contains(t)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private String clientDisplay(CrmEvent crm) {
        if (crm == null) {
            return null;
        }
        if (!NormalizationUtil.isBlank(crm.client_name) && !NormalizationUtil.isBlank(crm.client_company)) {
            return crm.client_name + " (" + crm.client_company + ")";
        }
        if (!NormalizationUtil.isBlank(crm.client_name)) {
            return crm.client_name;
        }
        if (!NormalizationUtil.isBlank(crm.client_company)) {
            return crm.client_company;
        }
        return null;
    }

    private void collectDataQualityNotes(UnifiedMeeting m,
                                         List<CrmEvent> crm, ParsedTime[] crmTimes,
                                         List<CalendarEvent> cal, ParsedTime[] calTimes,
                                         List<Integer> crmIdx, List<Integer> calIdx,
                                         Integer crmI, Integer calI) {
        List<String> notes = m.getDataQualityNotes();

        if (crmI != null && crmTimes[crmI].note != null) {
            notes.add("CRM " + crm.get(crmI).crm_id + ": " + crmTimes[crmI].note);
        }
        if (crmI != null && crmTimes[crmI].date != null && !crmTimes[crmI].hasTime()) {
            notes.add("CRM " + crm.get(crmI).crm_id + ": meeting_time missing");
        }
        if (calI != null && calTimes[calI].note != null) {
            notes.add("Calendar " + cal.get(calI).event_id + ": " + calTimes[calI].note);
        }

        if (calI != null && cal.get(calI).attendees != null) {
            for (String a : cal.get(calI).attendees) {
                if (a != null && (a.contains("[at]") || a.contains("(at)"))) {
                    notes.add("Calendar " + cal.get(calI).event_id
                            + ": repaired malformed attendee email '" + a + "' -> " + NormalizationUtil.fixEmail(a));
                }
            }
            if (cal.get(calI).attendees.isEmpty()) {
                notes.add("Calendar " + cal.get(calI).event_id + ": no attendees listed");
            }
        }
        if (calI != null && NormalizationUtil.isBlank(cal.get(calI).location)) {
            notes.add("Calendar " + cal.get(calI).event_id + ": location missing");
        }

        if (calIdx.size() > 1) {
            List<String> ids = new ArrayList<>();
            for (int j : calIdx) {
                ids.add(cal.get(j).event_id);
            }
            notes.add("Collapsed " + calIdx.size() + " calendar records that appear to be duplicates: " + ids);
            for (int j : calIdx) {
                if (calI != null && j != calI) {
                    String otherLoc = cal.get(j).location;
                    String repLoc = cal.get(calI).location;
                    if (otherLoc != null && repLoc != null && !otherLoc.equalsIgnoreCase(repLoc)) {
                        notes.add("Duplicate " + cal.get(j).event_id + " disagrees on location: '"
                                + otherLoc + "' vs '" + repLoc + "'");
                    }
                }
            }
        }
        if (crmIdx.size() > 1) {
            List<String> ids = new ArrayList<>();
            for (int i : crmIdx) {
                ids.add(crm.get(i).crm_id);
            }
            notes.add("Collapsed " + crmIdx.size() + " CRM records that appear to be duplicates: " + ids);
        }
    }

    private ReconciliationStats computeStats(List<UnifiedMeeting> built, int nCrm, int nCal, int duplicatesCollapsed) {
        ReconciliationStats s = new ReconciliationStats();
        s.setTotalMeetings(built.size());
        s.setCrmRecordsIngested(nCrm);
        s.setCalendarRecordsIngested(nCal);
        s.setDuplicateRecordsCollapsed(duplicatesCollapsed);
        int matched = 0;
        int crmOnly = 0;
        int calOnly = 0;
        int conflicts = 0;
        for (UnifiedMeeting m : built) {
            boolean hasCrm = m.getSources().contains(MeetingSource.CRM);
            boolean hasCal = m.getSources().contains(MeetingSource.CALENDAR);
            if (hasCrm && hasCal) {
                matched++;
            } else if (hasCrm) {
                crmOnly++;
            } else {
                calOnly++;
            }
            if (m.hasConflicts()) {
                conflicts++;
            }
        }
        s.setMatchedBothSources(matched);
        s.setCrmOnly(crmOnly);
        s.setCalendarOnly(calOnly);
        s.setMeetingsWithConflicts(conflicts);
        return s;
    }

    private static String emptyToNull(String s) {
        return NormalizationUtil.isBlank(s) ? null : s;
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] size;

        UnionFind(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (size[ra] < size[rb]) {
                int tmp = ra;
                ra = rb;
                rb = tmp;
            }
            parent[rb] = ra;
            size[ra] += size[rb];
        }
    }
}
