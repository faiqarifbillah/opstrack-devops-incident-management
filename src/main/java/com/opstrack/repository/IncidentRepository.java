package com.opstrack.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.opstrack.model.Incident;
import com.opstrack.model.Severity;
import com.opstrack.model.Status;

/** Spring Data generates the SQL from these method names. */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(Status status, Sort sort);

    List<Incident> findBySeverity(Severity severity, Sort sort);
}
