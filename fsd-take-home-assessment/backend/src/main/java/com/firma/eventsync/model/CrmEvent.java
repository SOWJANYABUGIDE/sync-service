package com.firma.eventsync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrmEvent {

    public String crm_id;
    public String subject;
    public String client_name;
    public String client_company;
    public String relationship_owner;
    public String meeting_date;
    public String meeting_time;
    public String meeting_type;
    public String location;
    public String notes;
    public String status;
    public String created_at;
}
