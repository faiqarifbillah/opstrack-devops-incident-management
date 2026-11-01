package com.opstrack.dto;

import java.time.Instant;

import com.opstrack.model.Incident;
import com.opstrack.model.Severity;
import com.opstrack.model.Status;

public record IncidentResponse(
        Long id,
        String title,
        String description,
        Severity severity,
        Status status,
        String assignedTo,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getAssignedTo(),
                incident.getResolutionNotes(),
                incident.getCreatedAt(),
                incident.getUpdatedAt());
    }
}
