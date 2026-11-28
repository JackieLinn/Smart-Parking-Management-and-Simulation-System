/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.service;

import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
import tiei.ajp.project.model.ParkingUser;
import tiei.ajp.project.model.PatrolMessage;
import tiei.ajp.project.model.PatrolOfficer;
import tiei.ajp.project.model.SecurityIncident;
import tiei.ajp.project.model.SpaceOccupancy;
import tiei.ajp.project.repository.InMemorySecurityIncidentRepository;
import tiei.ajp.project.repository.ParkingSessionRepository;
import tiei.ajp.project.repository.SecurityIncidentRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Coordinates space allocation and parking events. The session validates state transitions and the repository saves the results.
 */
public class ParkingService {
    private final List<ParkingSpace> spaces;
    private final ParkingSessionRepository sessions;
    private final WrongParkingService wrongParking;

    public ParkingService(List<ParkingSpace> spaces, ParkingSessionRepository sessions) {
        this(spaces, sessions, new InMemorySecurityIncidentRepository(), List.of(),
                Duration.ofMinutes(3), new BigDecimal("50.00"));
    }

    public ParkingService(List<ParkingSpace> spaces, ParkingSessionRepository sessions,
                          SecurityIncidentRepository incidents, List<PatrolOfficer> officers,
                          Duration moveGracePeriod, BigDecimal wrongParkingFine) {
        this.spaces = List.copyOf(spaces);
        this.sessions = sessions;
        this.wrongParking = new WrongParkingService(incidents, officers, moveGracePeriod, wrongParkingFine);
    }

    /**
     * Reserves the first available space and returns the session ID for subsequent events.
     */
    public long reserveSpace(String licensePlate, Instant at) {
        return reserveSpace(licensePlate, null, at);
    }

    /**
     * Reserves a space for a known account, or for an occasional user if no account is supplied.
     */
    public synchronized long reserveSpace(String licensePlate, ParkingUser user, Instant at) {
        String plate = licensePlate.strip().toUpperCase(Locale.ROOT);
        if (plate.isEmpty()) {
            throw new IllegalArgumentException("License plate is required");
        }

        List<ParkingSession> existingSessions = sessions.findAll();
        // A plate can start a new session only after the previous session records the vehicle's exit.
        boolean hasActiveSession = existingSessions.stream()
                .anyMatch(session -> session.plate().equals(plate) && session.status() != ParkingSession.Status.EXITED);
        if (hasActiveSession) {
            throw new IllegalStateException("Vehicle already has an active parking session");
        }

        // Check reservations and actual occupancy separately so a space occupied by the wrong vehicle is unavailable.
        ParkingSpace space = spaces.stream()
                .filter(candidate -> existingSessions.stream().noneMatch(session ->
                        reservesSpace(session, candidate.id()) || session.occupiesSpace(candidate.id())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parking space available"));

        long sessionId = sessions.nextId();
        sessions.save(new ParkingSession(sessionId, plate, space.id(), at, user));
        return sessionId;
    }

    public synchronized void confirmEntry(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmEntry(at);
        sessions.save(session);
    }

    public synchronized void confirmParking(long sessionId, long observedSpaceId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        if (spaces.stream().noneMatch(space -> space.id() == observedSpaceId)) {
            throw new IllegalArgumentException("Unknown parking space: " + observedSpaceId);
        }
        List<ParkingSession> otherSessions = sessions.findAll().stream()
                .filter(other -> other.id() != sessionId).toList();
        if (otherSessions.stream().anyMatch(other -> other.occupiesSpace(observedSpaceId))) {
            throw new IllegalStateException("Space already has a confirmed occupant");
        }

        boolean assignedToAnother = otherSessions.stream().anyMatch(other -> reservesSpace(other, observedSpaceId));
        boolean correctlyParked = observedSpaceId == session.spaceId() && !assignedToAnother;
        session.confirmParking(observedSpaceId, at, correctlyParked);
        if (correctlyParked) {
            wrongParking.close(sessionId, true, at);
        } else {
            // If the vehicle parks incorrectly again, its original space may already be reserved for another vehicle.
            boolean assignedSpaceTaken = otherSessions.stream().anyMatch(other ->
                    reservesSpace(other, session.spaceId()) || other.occupiesSpace(session.spaceId()));
            if (!assignedSpaceTaken) {
                session.retainAssignedSpace();
            }
            wrongParking.observeWrongParking(session, observedSpaceId, at);
        }
        sessions.save(session);
    }

    /**
     * Ends the current occupancy and checks whether the vehicle left after the move deadline.
     */
    public synchronized void confirmSpaceDeparture(long sessionId, Instant at) {
        confirmSpaceDeparture(requireSession(sessionId), at);
    }

    public synchronized void confirmSpaceDeparture(long sessionId, long observedSpaceId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        if (!session.occupiesSpace(observedSpaceId)) {
            throw new IllegalStateException("Departure does not match the current occupied space");
        }
        confirmSpaceDeparture(session, at);
    }

    private void confirmSpaceDeparture(ParkingSession session, Instant at) {
        session.confirmSpaceDeparture(at);
        if (!session.lastOccupancy().correctlyParked()) {
            wrongParking.checkDeadline(session.id(), at);
        }
        sessions.save(session);
    }

    /**
     * Checks for unresolved wrong parking at the exit. Call {@code wrongParkingFineTotal} before settlement.
     */
    public synchronized void confirmExitArrival(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmExitArrival(at);
        wrongParking.onExitArrival(sessionId, at);
        sessions.save(session);
    }

    public synchronized void confirmPayment(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        if (wrongParking.needsReview(sessionId)) {
            throw new IllegalStateException("Wrong parking requires review before settlement");
        }
        if (session.status() == ParkingSession.Status.LEFT_SPACE) {
            confirmExitArrival(sessionId, at);
        }
        session.confirmPayment(at);
        sessions.save(session);
    }

    public synchronized void confirmExit(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmExit(at);
        sessions.save(session);
    }

    public synchronized List<SecurityIncident> securityIncidents() {
        return wrongParking.allIncidents();
    }

    public synchronized BigDecimal wrongParkingFineTotal(long sessionId) {
        requireSession(sessionId);
        return wrongParking.fineTotal(sessionId);
    }

    public synchronized void reportPatrolMessage(long incidentId, PatrolMessage message) {
        wrongParking.report(incidentId, message);
    }

    /**
     * Process device events through the current simulation time before checking move deadlines.
     */
    public synchronized void checkMoveDeadlines(Instant at) {
        for (ParkingSession session : sessions.findAll()) {
            if (session.status() == ParkingSession.Status.PARKED) {
                SpaceOccupancy occupancy = session.lastOccupancy();
                if (!occupancy.correctlyParked() && !occupancy.parkedAt().isAfter(at)) {
                    wrongParking.checkDeadline(session.id(), at);
                }
            }
        }
    }

    public synchronized void reportOccupancyUncertain(long incidentId) {
        wrongParking.requestReview(incidentId);
    }

    public synchronized void completeOccupancyReview(long incidentId, boolean upholdFine, Instant at) {
        wrongParking.completeReview(incidentId, upholdFine, at);
    }

    private boolean reservesSpace(ParkingSession session, long spaceId) {
        return session.spaceId() == spaceId && session.holdsSpace();
    }

    private ParkingSession requireSession(long sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown parking session: " + sessionId));
    }
}
