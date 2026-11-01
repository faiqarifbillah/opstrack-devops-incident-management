package com.opstrack.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opstrack.dto.IncidentRequest;
import com.opstrack.dto.IncidentResponse;
import com.opstrack.exception.IncidentNotFoundException;
import com.opstrack.model.Incident;
import com.opstrack.model.Severity;
import com.opstrack.model.Status;
import com.opstrack.repository.IncidentRepository;

@Service
public class IncidentService {

    private static final Sort BY_ID = Sort.by("id");

    private final IncidentRepository repository;

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        Incident incident = new Incident();
        apply(request, incident);
        return IncidentResponse.from(repository.save(incident));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> findAll() {
        return toResponses(repository.findAll(BY_ID));
    }

    @Transactional(readOnly = true)
    public IncidentResponse findById(Long id) {
        return IncidentResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> findByStatus(Status status) {
        return toResponses(repository.findByStatus(status, BY_ID));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> findBySeverity(Severity severity) {
        return toResponses(repository.findBySeverity(severity, BY_ID));
    }

    @Transactional
    public IncidentResponse update(Long id, IncidentRequest request) {
        Incident incident = getOrThrow(id);
        apply(request, incident);
        // Flush now so @PreUpdate sets updatedAt before the response is built.
        return IncidentResponse.from(repository.saveAndFlush(incident));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    private Incident getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    private void apply(IncidentRequest request, Incident incident) {
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setSeverity(request.severity());
        incident.setStatus(request.status() != null ? request.status() : Status.OPEN);
        incident.setAssignedTo(request.assignedTo());
        incident.setResolutionNotes(request.resolutionNotes());
    }

    private List<IncidentResponse> toResponses(List<Incident> incidents) {
        return incidents.stream().map(IncidentResponse::from).toList();
    }
}
