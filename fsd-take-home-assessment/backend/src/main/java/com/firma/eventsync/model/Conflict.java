package com.firma.eventsync.model;

public class Conflict {

    private String field;
    private Object crmValue;
    private Object calendarValue;

    public Conflict() {
    }

    public Conflict(String field, Object crmValue, Object calendarValue) {
        this.field = field;
        this.crmValue = crmValue;
        this.calendarValue = calendarValue;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getCrmValue() {
        return crmValue;
    }

    public void setCrmValue(Object crmValue) {
        this.crmValue = crmValue;
    }

    public Object getCalendarValue() {
        return calendarValue;
    }

    public void setCalendarValue(Object calendarValue) {
        this.calendarValue = calendarValue;
    }
}
