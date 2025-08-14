package com.voxstream.core.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voxstream.core.event.BaseEvent;
import com.voxstream.core.event.Event;
import com.voxstream.core.event.EventMetadata;
import com.voxstream.core.event.EventType;

/**
 * Event persistence service using JdbcTemplate (H2 initial implementation).
 */
@Service
public class EventPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(EventPersistenceService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<Event> mapper = new RowMapper<Event>() {
        @Override
        public Event mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString("id");
            EventType type = EventType.valueOf(rs.getString("type"));
            String source = rs.getString("source_platform");
            Instant created = rs.getTimestamp("created_at").toInstant();
            Timestamp expiresTs = rs.getTimestamp("expires_at");
            Instant expires = expiresTs != null ? expiresTs.toInstant() : null;
            int importance = rs.getInt("importance");
            String correlationId = rs.getString("correlation_id");
            String payloadJson = rs.getString("payload");
            Map<String, Object> payload = payloadJson != null && !payloadJson.isBlank() ? readPayload(payloadJson)
                    : Map.of();
            EventMetadata metadata = EventMetadata.builder().importance(importance).expiresAt(expires)
                    .correlationId(correlationId).build();
            return new BaseEvent(id, type, created, source, payload, metadata);
        }
    };

    @Autowired
    public EventPersistenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(Event event) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO events (id, type, source_platform, created_at, expires_at, importance, correlation_id, payload) VALUES (?,?,?,?,?,?,?,?)",
                    event.getId(), event.getType().name(), event.getSourcePlatform(),
                    Timestamp.from(event.getTimestamp()),
                    event.getMetadata() != null && event.getMetadata().getExpiresAt().isPresent()
                            ? Timestamp.from(event.getMetadata().getExpiresAt().get())
                            : null,
                    event.getMetadata() != null ? event.getMetadata().getImportance() : 0,
                    event.getMetadata() != null ? event.getMetadata().getCorrelationId() : event.getId(),
                    writePayload(event.getPayload()));
        } catch (Exception e) {
            logger.warn("Failed to persist event {}: {}", event.getId(), e.getMessage());
        }
    }

    public List<Event> findRecent(int limit) {
        return jdbcTemplate.query("SELECT * FROM events ORDER BY created_at DESC LIMIT ?", mapper, limit);
    }

    public int purgeOlderThan(long epochMillis) {
        return jdbcTemplate.update("DELETE FROM events WHERE created_at < ?", new Timestamp(epochMillis));
    }

    private String writePayload(Map<String, Object> payload) throws JsonProcessingException {
        if (payload == null || payload.isEmpty())
            return null;
        return objectMapper.writeValueAsString(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.debug("Failed to deserialize payload: {}", e.getMessage());
            return Map.of();
        }
    }
}
