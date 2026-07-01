package com.firma.eventsync.model;

public class FieldProvenance {

    private String field;
    private Object value;
    private Object crmValue;
    private Object calendarValue;
    private boolean conflict;

    public FieldProvenance() {
    }

    public FieldProvenance(String field, Object value, Object crmValue,
                           Object calendarValue, boolean conflict) {
        this.field = field;
        this.value = value;
        this.crmValue = crmValue;
        this.calendarValue = calendarValue;
        this.conflict = conflict;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
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

    public boolean isConflict() {
        return conflict;
    }

    public void setConflict(boolean conflict) {
        this.conflict = conflict;
    }
}
