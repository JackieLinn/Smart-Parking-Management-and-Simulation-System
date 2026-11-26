package tiei.ajp.project.service;

import org.junit.jupiter.api.Test;
import tiei.ajp.project.model.Fine;
import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
import tiei.ajp.project.model.PatrolMessage;
import tiei.ajp.project.model.PatrolOfficer;
import tiei.ajp.project.model.SecurityIncident;
import tiei.ajp.project.model.SpaceOccupancy;
import tiei.ajp.project.repository.InMemoryParkingSessionRepository;
import tiei.ajp.project.repository.InMemorySecurityIncidentRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrongParkingFlowTest {
    private static final long OFFICER_ID = 1L;
    private static final BigDecimal FINE_AMOUNT = new BigDecimal("50.00");
    private static final Instant START = Instant.parse("2026-11-23T08:00:00Z");

    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
    private final ParkingService parking = new ParkingService(
            List.of(new ParkingSpace(101L, "B-X9"), new ParkingSpace(102L, "B-X10"),
                    new ParkingSpace(103L, "B-X11"), new ParkingSpace(104L, "B-X12")),
            sessions, new InMemorySecurityIncidentRepository(),
            List.of(new PatrolOfficer(OFFICER_ID, "P01", "巡逻员 01")), Duration.ofMinutes(3), FINE_AMOUNT);

    @Test
    void movingAtDeadlineClosesIncidentAndBillsOnlyActualOccupancy() {
        long sessionId = startWrongParking();
        SecurityIncident incident = firstIncident();
        assertEquals(101L, incident.assignedSpaceId());
        assertEquals(102L, incident.lastWrongSpaceId());
        assertEquals(SecurityIncident.Status.ASSIGNED, incident.status());
        assertEquals(103L, session(parking.reserveSpace("OTHER", minute(1))).spaceId());

        parking.reportPatrolMessage(incident.id(),
                new PatrolMessage(OFFICER_ID, PatrolMessage.Type.ARRIVED, "已到场", minute(2)));
        parking.checkMoveDeadlines(minute(8));
        assertNull(incident.moveDeadline());
        assertNull(incident.fine());

        notifyDriver(incident, minute(10));
        notifyDriver(incident, minute(11));
        assertEquals(minute(13), incident.moveDeadline());
        assertEquals(2, incident.messages().size());
        parking.checkMoveDeadlines(minute(13));
        parking.confirmSpaceDeparture(sessionId, 102L, minute(13));
        assertTrue(session(sessionId).holdsSpace());
        assertEquals(102L, session(parking.reserveSpace("NEXT", minute(13))).spaceId());
        parking.checkMoveDeadlines(minute(14));
        parking.confirmParking(sessionId, 101L, minute(15));
        assertEquals(SecurityIncident.Status.RESOLVED, incident.status());
        assertNull(incident.fine());
        assertThrows(IllegalStateException.class,
                () -> parking.confirmSpaceDeparture(sessionId, 102L, minute(16)));

        parking.confirmSpaceDeparture(sessionId, 101L, minute(18));
        parking.confirmExitArrival(sessionId, minute(19));
        parking.confirmPayment(sessionId, minute(19));
        parking.confirmExit(sessionId, minute(20));
        ParkingSession completed = session(sessionId);
        assertEquals(List.of(102L, 101L), completed.occupancies().stream().map(SpaceOccupancy::spaceId).toList());
        assertEquals(Duration.ofMinutes(15), completed.billableDuration());
        assertEquals(ParkingSession.Status.EXITED, completed.status());
    }

    @Test
    void changingWrongSpacesKeepsDeadlineAndIssuesOneFine() {
        long sessionId = startWrongParking();
        SecurityIncident incident = firstIncident();
        notifyDriver(incident, minute(2));
        parking.confirmSpaceDeparture(sessionId, minute(3));
        parking.confirmParking(sessionId, 103L, minute(4));
        assertEquals(minute(5), incident.moveDeadline());
        assertEquals(103L, incident.lastWrongSpaceId());

        parking.checkMoveDeadlines(minute(6));
        parking.checkMoveDeadlines(minute(7));
        parking.confirmSpaceDeparture(sessionId, minute(8));
        parking.confirmExitArrival(sessionId, minute(9));
        assertEquals(1, parking.securityIncidents().size());
        assertEquals(Fine.Reason.MOVE_DEADLINE_EXCEEDED, incident.fine().reason());
        assertEquals(minute(6), incident.fine().issuedAt());
        assertEquals(FINE_AMOUNT, parking.wrongParkingFineTotal(sessionId));
        assertTrue(session(sessionId).holdsSpace());

        parking.confirmPayment(sessionId, minute(9));
        parking.confirmExit(sessionId, minute(10));
        assertFalse(session(sessionId).holdsSpace());
    }

    @Test
    void lateDepartureIssuesFineEvenWithoutADeadlineCheck() {
        long sessionId = startWrongParking();
        notifyDriver(firstIncident(), minute(2));
        parking.confirmSpaceDeparture(sessionId, minute(6));
        parking.confirmParking(sessionId, 101L, minute(7));
        assertEquals(SecurityIncident.Status.RESOLVED, firstIncident().status());
        assertEquals(FINE_AMOUNT, parking.wrongParkingFineTotal(sessionId));
    }

    @Test
    void wrongParkingAfterMovingPastTheDeadlineTriggersTheExistingIncident() {
        long sessionId = startWrongParking();
        SecurityIncident incident = firstIncident();
        notifyDriver(incident, minute(2));
        parking.confirmSpaceDeparture(sessionId, 102L, minute(4));

        // 截止时仍在行驶，等下一次停入事件再判断是否超时。
        parking.checkMoveDeadlines(minute(6));
        assertNull(incident.fine());
        parking.confirmParking(sessionId, 103L, minute(7));
        assertEquals(1, parking.securityIncidents().size());
        assertEquals(minute(5), incident.moveDeadline());
        assertEquals(Fine.Reason.MOVE_DEADLINE_EXCEEDED, incident.fine().reason());
        assertEquals(minute(7), incident.fine().issuedAt());
    }

    @Test
    void aSecondWrongParkingAfterCorrectionCreatesANewIncident() {
        long sessionId = startWrongParking();
        notifyDriver(firstIncident(), minute(2));
        parking.confirmSpaceDeparture(sessionId, minute(3));
        parking.confirmParking(sessionId, 101L, minute(4));
        parking.confirmSpaceDeparture(sessionId, minute(5));
        parking.confirmParking(sessionId, 103L, minute(6));

        assertEquals(2, parking.securityIncidents().size());
        SecurityIncident next = parking.securityIncidents().getLast();
        assertEquals(SecurityIncident.Status.RESOLVED, firstIncident().status());
        assertNull(next.moveDeadline());
        notifyDriver(next, minute(7));
        assertEquals(minute(10), next.moveDeadline());
    }

    @Test
    void busyOfficerLeavesNextIncidentQueuedUntilNotificationCompletes() {
        startWrongParking();
        long secondId = parking.reserveSpace("SECOND", minute(2));
        parking.confirmEntry(secondId, minute(2));
        parking.confirmParking(secondId, 104L, minute(3));
        SecurityIncident queued = parking.securityIncidents().getLast();
        parking.checkMoveDeadlines(minute(10));
        assertEquals(SecurityIncident.Status.QUEUED, queued.status());
        assertNull(queued.assignment());
        assertNull(queued.fine());

        notifyDriver(firstIncident(), minute(11));
        assertEquals(SecurityIncident.Status.ASSIGNED, queued.status());
        assertEquals(minute(11), queued.assignment().assignedAt());
        assertNull(queued.moveDeadline());
        notifyDriver(queued, minute(12));
        assertEquals(minute(15), queued.moveDeadline());
    }

    @Test
    void arrivingAtExitWithoutNoticeKeepsIncidentButDoesNotIssueFine() {
        long sessionId = startWrongParking();
        parking.confirmSpaceDeparture(sessionId, minute(2));
        parking.confirmExitArrival(sessionId, minute(3));
        assertEquals(SecurityIncident.Status.CLOSED_UNCORRECTED, firstIncident().status());
        assertNull(firstIncident().fine());
    }

    @Test
    void leavingAfterNoticeAndGoingStraightToExitIsFined() {
        long sessionId = startWrongParking();
        notifyDriver(firstIncident(), minute(2));
        parking.confirmSpaceDeparture(sessionId, minute(3));
        parking.confirmExitArrival(sessionId, minute(4));
        assertEquals(Fine.Reason.EXIT_WITHOUT_CORRECTION, firstIncident().fine().reason());
        assertEquals(FINE_AMOUNT, parking.wrongParkingFineTotal(sessionId));
    }

    @Test
    void unclearOccupancyNeedsReviewBeforePayment() {
        long sessionId = startWrongParking();
        SecurityIncident incident = firstIncident();
        notifyDriver(incident, minute(2));
        parking.reportOccupancyUncertain(incident.id());
        parking.checkMoveDeadlines(minute(6));
        assertNull(incident.fine());

        parking.confirmSpaceDeparture(sessionId, minute(7));
        parking.confirmExitArrival(sessionId, minute(8));
        assertEquals(SecurityIncident.Status.REVIEW_REQUIRED, incident.status());
        assertThrows(IllegalStateException.class, () -> parking.confirmPayment(sessionId, minute(8)));
        parking.completeOccupancyReview(incident.id(), false, minute(9));
        parking.confirmPayment(sessionId, minute(9));
        assertEquals(SecurityIncident.Status.CLOSED_UNCORRECTED, incident.status());
        assertNull(incident.fine());
    }

    @Test
    void wrongParkingDoesNotRemoveAnotherVehiclesReservation() {
        long firstId = parking.reserveSpace("FIRST", START);
        long secondId = parking.reserveSpace("SECOND", START);
        parking.confirmEntry(firstId, START);
        parking.confirmEntry(secondId, START);
        parking.confirmParking(firstId, 102L, minute(1));
        assertThrows(IllegalStateException.class, () -> parking.confirmParking(secondId, 102L, minute(2)));
        parking.confirmSpaceDeparture(firstId, 102L, minute(3));
        assertEquals(103L, session(parking.reserveSpace("THIRD", minute(3))).spaceId());
        parking.confirmParking(secondId, 102L, minute(4));
        assertTrue(session(secondId).lastOccupancy().correctlyParked());
    }

    private long startWrongParking() {
        long sessionId = parking.reserveSpace("FIRST", START);
        parking.confirmEntry(sessionId, START);
        parking.confirmParking(sessionId, 102L, minute(1));
        return sessionId;
    }

    private ParkingSession session(long sessionId) {
        return sessions.findById(sessionId).orElseThrow();
    }

    private SecurityIncident firstIncident() {
        return parking.securityIncidents().getFirst();
    }

    private void notifyDriver(SecurityIncident incident, Instant at) {
        parking.reportPatrolMessage(incident.id(),
                new PatrolMessage(OFFICER_ID, PatrolMessage.Type.DRIVER_NOTIFIED, "已提醒司机移车", at));
    }

    private Instant minute(long minutes) {
        return START.plus(Duration.ofMinutes(minutes));
    }
}
