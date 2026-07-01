package com.firma.eventsync.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firma.eventsync.model.CalendarEvent;
import com.firma.eventsync.model.CrmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class DataLoader {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private static final String CRM_FILE = "data/crm_events.json";
    private static final String CALENDAR_FILE = "data/calendar_events.json";

    private final ObjectMapper objectMapper;

    public DataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CrmEvent> loadCrmEvents() {
        List<CrmEvent> events = read(CRM_FILE, CrmEvent[].class);
        log.info("Loaded {} CRM events from {}", events.size(), CRM_FILE);
        return events;
    }

    public List<CalendarEvent> loadCalendarEvents() {
        List<CalendarEvent> events = read(CALENDAR_FILE, CalendarEvent[].class);
        log.info("Loaded {} calendar events from {}", events.size(), CALENDAR_FILE);
        return events;
    }

    private <T> List<T> read(String path, Class<T[]> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            T[] array = objectMapper.readValue(in, type);
            return List.of(array);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load data file: " + path, e);
        }
    }
}
