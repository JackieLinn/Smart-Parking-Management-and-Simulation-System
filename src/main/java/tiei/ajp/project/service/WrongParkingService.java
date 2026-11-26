package tiei.ajp.project.service;

import tiei.ajp.project.model.Fine;
import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.PatrolAssignment;
import tiei.ajp.project.model.PatrolMessage;
import tiei.ajp.project.model.PatrolOfficer;
import tiei.ajp.project.model.SecurityIncident;
import tiei.ajp.project.repository.SecurityIncidentRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 由停车服务调用，管理错停告警、巡逻队列和罚款。
 */
public class WrongParkingService {
    private final SecurityIncidentRepository incidents;
    private final List<PatrolOfficer> officers;
    private final Duration gracePeriod;
    private final BigDecimal fineAmount;

    WrongParkingService(SecurityIncidentRepository incidents, List<PatrolOfficer> officers,
                        Duration gracePeriod, BigDecimal fineAmount) {
        if (gracePeriod.isNegative() || gracePeriod.isZero() || fineAmount.signum() < 0) {
            throw new IllegalArgumentException("Grace period must be positive and fine cannot be negative");
        }
        this.incidents = incidents;
        this.officers = List.copyOf(officers);
        this.gracePeriod = gracePeriod;
        this.fineAmount = fineAmount;
    }

    void observeWrongParking(ParkingSession session, long spaceId, Instant at) {
        SecurityIncident incident = openIncident(session.id());
        if (incident == null) {
            incident = new SecurityIncident(incidents.nextId(), session.id(), session.spaceId(), spaceId, at);
        } else {
            incident.observeAnotherWrongSpace(spaceId);
            checkDeadline(incident, at);
        }
        incidents.save(incident);
        dispatch(at);
    }

    void checkDeadline(long sessionId, Instant at) {
        SecurityIncident incident = openIncident(sessionId);
        if (incident != null) {
            checkDeadline(incident, at);
        }
    }

    private void checkDeadline(SecurityIncident incident, Instant at) {
        // 截止时刻的离位仍算及时。定时检查和设备事件使用同一模拟时间。
        if (incident.fine() == null && incident.allowsAutomaticFine() && at.isAfter(incident.moveDeadline())) {
            incident.issueFine(Fine.Reason.MOVE_DEADLINE_EXCEEDED, fineAmount, at);
            incidents.save(incident);
        }
    }

    void report(long incidentId, PatrolMessage message) {
        SecurityIncident incident = requireIncident(incidentId);
        incident.report(message, gracePeriod);
        incidents.save(incident);
        dispatch(message.sentAt());
    }

    void onExitArrival(long sessionId, Instant at) {
        SecurityIncident incident = openIncident(sessionId);
        if (incident != null && incident.fine() == null && incident.allowsAutomaticFine()) {
            incident.issueFine(Fine.Reason.EXIT_WITHOUT_CORRECTION, fineAmount, at);
        }
        close(sessionId, false, at);
    }

    void close(long sessionId, boolean corrected, Instant at) {
        SecurityIncident incident = openIncident(sessionId);
        if (incident != null) {
            incident.close(corrected, at);
            incidents.save(incident);
            dispatch(at);
        }
    }

    void requestReview(long incidentId) {
        SecurityIncident incident = requireIncident(incidentId);
        incident.requestReview();
        incidents.save(incident);
    }

    void completeReview(long incidentId, boolean upholdFine, Instant at) {
        SecurityIncident incident = requireIncident(incidentId);
        incident.completeReview(upholdFine, fineAmount, at);
        incidents.save(incident);
        dispatch(at);
    }

    boolean needsReview(long sessionId) {
        return incidents.findAll().stream().anyMatch(incident -> incident.sessionId() == sessionId
                && incident.status() == SecurityIncident.Status.REVIEW_REQUIRED);
    }

    List<SecurityIncident> allIncidents() {
        return incidents.findAll();
    }

    BigDecimal fineTotal(long sessionId) {
        return incidents.findAll().stream()
                .filter(incident -> incident.sessionId() == sessionId && incident.fine() != null)
                .map(incident -> incident.fine().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SecurityIncident openIncident(long sessionId) {
        return incidents.findAll().stream()
                .filter(incident -> incident.sessionId() == sessionId && incident.isOpen())
                .findFirst().orElse(null);
    }

    private SecurityIncident requireIncident(long incidentId) {
        return incidents.findAll().stream().filter(incident -> incident.id() == incidentId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown incident: " + incidentId));
    }

    private void dispatch(Instant at) {
        List<SecurityIncident> current = incidents.findAll();
        Set<Long> busyOfficers = new HashSet<>();
        for (SecurityIncident incident : current) {
            PatrolAssignment assignment = incident.assignment();
            if (assignment != null && assignment.completedAt() == null) {
                busyOfficers.add(assignment.officerId());
            }
        }

        List<SecurityIncident> queue = current.stream()
                .filter(incident -> incident.status() == SecurityIncident.Status.QUEUED && !incident.detectedAt().isAfter(at))
                .sorted(Comparator.comparing(SecurityIncident::detectedAt).thenComparingLong(SecurityIncident::id))
                .toList();
        int nextIncident = 0;
        for (PatrolOfficer officer : officers) {
            if (nextIncident == queue.size()) {
                break;
            }
            if (busyOfficers.add(officer.id())) {
                SecurityIncident incident = queue.get(nextIncident++);
                incident.assign(officer.id(), at);
                incidents.save(incident);
            }
        }
    }
}
