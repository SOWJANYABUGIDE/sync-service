package com.firma.eventsync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEvent {

    public String event_id;
    public String title;
    public String organizer;
    public List<String> attendees;
    public String start_time;
    public String end_time;
    public String location;
    public String description;
    public Boolean is_recurring;
    public String status;
    public String created_at;
}
