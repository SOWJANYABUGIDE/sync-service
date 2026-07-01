package com.firma.eventsync.service;

import com.firma.eventsync.model.CalendarEvent;
import com.firma.eventsync.model.CrmEvent;
import com.firma.eventsync.service.NormalizationUtil.ParsedTime;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MatchScorer {

    private static final double W_TIME = 0.35;
    private static final double W_PARTICIPANT = 0.30;
    private static final double W_OWNER = 0.10;
    private static final double W_TITLE = 0.25;

    private static final int DOMAIN_PREFIX_MATCH = 5;

    public double score(CrmEvent crm, ParsedTime crmTime,
                        CalendarEvent cal, ParsedTime calTime,
                        int timeWindowMinutes) {

        if (crmTime.date == null || calTime.date == null
                || !crmTime.date.equals(calTime.date)) {
            return 0.0;
        }

        double weighted = 0.0;
        double applicable = 0.0;

        if (crmTime.hasTime() && calTime.hasTime()) {
            long minutes = Math.abs(Duration.between(crmTime.dateTime, calTime.dateTime).toMinutes());
            double timeScore = minutes >= timeWindowMinutes
                    ? 0.0
                    : 1.0 - ((double) minutes / timeWindowMinutes);
            weighted += timeScore * W_TIME;
            applicable += W_TIME;
        }

        if (!NormalizationUtil.isBlank(crm.client_company) || !NormalizationUtil.isBlank(crm.client_name)) {
            double participantScore = participantMatch(crm, cal) ? 1.0 : 0.0;
            weighted += participantScore * W_PARTICIPANT;
            applicable += W_PARTICIPANT;
        }

        if (!NormalizationUtil.isBlank(crm.relationship_owner) && !NormalizationUtil.isBlank(cal.organizer)) {
            double ownerScore = ownerMatch(crm.relationship_owner, cal) ? 1.0 : 0.0;
            weighted += ownerScore * W_OWNER;
            applicable += W_OWNER;
        }

        double titleScore = NormalizationUtil.tokenSimilarity(crm.subject, cal.title);
        weighted += titleScore * W_TITLE;
        applicable += W_TITLE;

        return applicable == 0.0 ? 0.0 : weighted / applicable;
    }

    public boolean participantMatch(CrmEvent crm, CalendarEvent cal) {
        String companyCore = NormalizationUtil.companyCore(crm.client_company);
        List<String> nameTokens = NormalizationUtil.nameTokens(crm.client_name);

        for (String person : peopleOf(cal)) {
            String email = NormalizationUtil.fixEmail(person);

            String domainCore = NormalizationUtil.emailDomainCore(email);
            if (companyCore != null && domainCore != null
                    && NormalizationUtil.commonPrefixLength(companyCore, domainCore) >= DOMAIN_PREFIX_MATCH) {
                return true;
            }

            String localPart = NormalizationUtil.emailLocalPart(email);
            if (!nameTokens.isEmpty() && localPart != null && containsAll(localPart, nameTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean ownerMatch(String owner, CalendarEvent cal) {
        List<String> ownerTokens = NormalizationUtil.nameTokens(owner);
        if (ownerTokens.isEmpty()) {
            return false;
        }
        String organizerLocal = NormalizationUtil.emailLocalPart(NormalizationUtil.fixEmail(cal.organizer));
        if (organizerLocal != null && containsAll(organizerLocal, ownerTokens)) {
            return true;
        }
        for (String person : peopleOf(cal)) {
            String localPart = NormalizationUtil.emailLocalPart(NormalizationUtil.fixEmail(person));
            if (localPart != null && containsAll(localPart, ownerTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAll(String haystack, List<String> needles) {
        for (String n : needles) {
            if (!haystack.contains(n)) {
                return false;
            }
        }
        return true;
    }

    private List<String> peopleOf(CalendarEvent cal) {
        List<String> people = new ArrayList<>();
        if (!NormalizationUtil.isBlank(cal.organizer)) {
            people.add(cal.organizer);
        }
        if (cal.attendees != null) {
            for (String a : cal.attendees) {
                if (!NormalizationUtil.isBlank(a)) {
                    people.add(a);
                }
            }
        }
        return people;
    }
}
