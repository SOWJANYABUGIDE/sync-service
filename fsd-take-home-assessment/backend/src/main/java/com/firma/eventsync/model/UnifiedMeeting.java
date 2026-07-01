package com.firma.eventsync.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnifiedMeeting {

    private String id;

    private String title;
    private String date;
    private String startTime;
    private String endTime;
    private String organizer;
    private String clientName;
    private String clientCompany;
    private String location;
    private String meetingType;
    private String status;
    private Boolean recurring;
    private List<String> participants = new ArrayList<>();

    private List<MeetingSource> sources = new ArrayList<>();
    private String crmId;
    private List<String> calendarIds = new ArrayList<>();
    private double matchScore;
    private Map<String, FieldProvenance> fields = new LinkedHashMap<>();
    private List<Conflict> conflicts = new ArrayList<>();
    private List<String> dataQualityNotes = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientCompany() {
        return clientCompany;
    }

    public void setClientCompany(String clientCompany) {
        this.clientCompany = clientCompany;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMeetingType() {
        return meetingType;
    }

    public void setMeetingType(String meetingType) {
        this.meetingType = meetingType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getRecurring() {
        return recurring;
    }

    public void setRecurring(Boolean recurring) {
        this.recurring = recurring;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }

    public List<MeetingSource> getSources() {
        return sources;
    }

    public void setSources(List<MeetingSource> sources) {
        this.sources = sources;
    }

    public String getCrmId() {
        return crmId;
    }

    public void setCrmId(String crmId) {
        this.crmId = crmId;
    }

    public List<String> getCalendarIds() {
        return calendarIds;
    }

    public void setCalendarIds(List<String> calendarIds) {
        this.calendarIds = calendarIds;
    }

    public double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(double matchScore) {
        this.matchScore = matchScore;
    }

    public Map<String, FieldProvenance> getFields() {
        return fields;
    }

    public void setFields(Map<String, FieldProvenance> fields) {
        this.fields = fields;
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<Conflict> conflicts) {
        this.conflicts = conflicts;
    }

    public List<String> getDataQualityNotes() {
        return dataQualityNotes;
    }

    public void setDataQualityNotes(List<String> dataQualityNotes) {
        this.dataQualityNotes = dataQualityNotes;
    }

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }
}
