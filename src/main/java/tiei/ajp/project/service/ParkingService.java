package tiei.ajp.project.service;

import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
import tiei.ajp.project.repository.ParkingSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 协调车位分配和停车事件。会话负责校验状态，仓储负责保存结果。
 */
public class ParkingService {
    private final List<ParkingSpace> spaces;
    private final ParkingSessionRepository sessions;

    public ParkingService(List<ParkingSpace> spaces, ParkingSessionRepository sessions) {
        this.spaces = List.copyOf(spaces);
        this.sessions = sessions;
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

        // 按车位列表顺序查找，跳过已被其他会话预留或占用的车位。
        ParkingSpace space = spaces.stream()
                .filter(candidate -> existingSessions.stream().noneMatch(session ->
                        session.spaceId() == candidate.id() && session.holdsSpace()))
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
        session.confirmParking(observedSpaceId, at);
        sessions.save(session);
    }

    /**
     * 确认车辆离开车位，释放车位并保留会话供出口结算。
     */
    public synchronized void confirmSpaceDeparture(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmSpaceDeparture(at);
        sessions.save(session);
    }

    public synchronized void confirmPayment(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmPayment(at);
        sessions.save(session);
    }

    public synchronized void confirmExit(long sessionId, Instant at) {
        ParkingSession session = requireSession(sessionId);
        session.confirmExit(at);
        sessions.save(session);
    }

    private ParkingSession requireSession(long sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown parking session: " + sessionId));
    }
}
