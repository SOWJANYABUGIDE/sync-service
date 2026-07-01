package com.firma.eventsync.model;

public class ReconciliationStats {

    private int totalMeetings;
    private int matchedBothSources;
    private int crmOnly;
    private int calendarOnly;
    private int meetingsWithConflicts;
    private int duplicateRecordsCollapsed;
    private int crmRecordsIngested;
    private int calendarRecordsIngested;

    public int getTotalMeetings() {
        return totalMeetings;
    }

    public void setTotalMeetings(int totalMeetings) {
        this.totalMeetings = totalMeetings;
    }

    public int getMatchedBothSources() {
        return matchedBothSources;
    }

    public void setMatchedBothSources(int matchedBothSources) {
        this.matchedBothSources = matchedBothSources;
    }

    public int getCrmOnly() {
        return crmOnly;
    }

    public void setCrmOnly(int crmOnly) {
        this.crmOnly = crmOnly;
    }

    public int getCalendarOnly() {
        return calendarOnly;
    }

    public void setCalendarOnly(int calendarOnly) {
        this.calendarOnly = calendarOnly;
    }

    public int getMeetingsWithConflicts() {
        return meetingsWithConflicts;
    }

    public void setMeetingsWithConflicts(int meetingsWithConflicts) {
        this.meetingsWithConflicts = meetingsWithConflicts;
    }

    public int getDuplicateRecordsCollapsed() {
        return duplicateRecordsCollapsed;
    }

    public void setDuplicateRecordsCollapsed(int duplicateRecordsCollapsed) {
        this.duplicateRecordsCollapsed = duplicateRecordsCollapsed;
    }

    public int getCrmRecordsIngested() {
        return crmRecordsIngested;
    }

    public void setCrmRecordsIngested(int crmRecordsIngested) {
        this.crmRecordsIngested = crmRecordsIngested;
    }

    public int getCalendarRecordsIngested() {
        return calendarRecordsIngested;
    }

    public void setCalendarRecordsIngested(int calendarRecordsIngested) {
        this.calendarRecordsIngested = calendarRecordsIngested;
    }
}
