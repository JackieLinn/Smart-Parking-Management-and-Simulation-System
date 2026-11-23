package tiei.ajp.project.repository;

import tiei.ajp.project.model.ParkingSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将会话按 ID 存在内存中。每次新建仓储都从空数据开始。
 */
public class InMemoryParkingSessionRepository implements ParkingSessionRepository {
    private final Map<Long, ParkingSession> sessions = new HashMap<>();
    private long nextId = 1L;

    @Override
    public long nextId() {
        return nextId++;
    }

    @Override
    public void save(ParkingSession session) {
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<ParkingSession> findById(long sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<ParkingSession> findAll() {
        return List.copyOf(sessions.values());
    }
}
