package tiei.ajp.project.service;

import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
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
 * 协调车位分配和停车事件。会话负责校验状态，仓储负责保存结果。
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
     * 预留第一个可用车位，返回用于后续操作的停车会话 ID。
     */
    public synchronized long reserveSpace(String licensePlate, Instant at) {
        String plate = licensePlate.strip().toUpperCase(Locale.ROOT);
        if (plate.isEmpty()) {
            throw new IllegalArgumentException("License plate is required");
        }

        List<ParkingSession> existingSessions = sessions.findAll();
        // 同一车牌的上一次会话完成出场后，才能创建下一次会话。
        boolean hasActiveSession = existingSessions.stream()
                .anyMatch(session -> session.plate().equals(plate) && session.status() != ParkingSession.Status.EXITED);
        if (hasActiveSession) {
            throw new IllegalStateException("Vehicle already has an active parking session");
        }

        // 预留和实际占用分别检查，错停车位也要从可分配列表排除。
        ParkingSpace space = spaces.stream()
                .filter(candidate -> existingSessions.stream().noneMatch(session ->
                        reservesSpace(session, candidate.id()) || session.occupiesSpace(candidate.id())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parking space available"));

        long sessionId = sessions.nextId();
        sessions.save(new ParkingSession(sessionId, plate, space.id(), at));
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
            // 下次错停时，原车位可能已在正常离位后分给另一辆车。
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
     * 结束当段实际占用，并检查离位是否晚于移车截止时间。
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

    /** 出口到达时判定是否逃避处理。结算前通过 wrongParkingFineTotal 查询罚款。 */
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

    /** 仿真时钟推进时，先处理到当前时刻的设备事件，再检查实占是否超时。 */
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
