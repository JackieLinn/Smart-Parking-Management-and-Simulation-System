/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.repository;

import tiei.ajp.project.model.ParkingSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores sessions by ID in memory. Each repository instance starts empty.
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
