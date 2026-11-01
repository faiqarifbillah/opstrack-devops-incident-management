package com.opstrack.dto;

import com.opstrack.model.Severity;
import com.opstrack.model.Status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Fields a client may set. The id and timestamps are controlled by the server. */
public record IncidentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @NotBlank(message = "description is required")
        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        @NotNull(message = "severity is required")
        Severity severity,

        Status status,

        @Size(max = 100, message = "assignedTo must be at most 100 characters")
        String assignedTo,

        @Size(max = 4000, message = "resolutionNotes must be at most 4000 characters")
        String resolutionNotes) {
}
