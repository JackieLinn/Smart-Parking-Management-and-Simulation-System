/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.repository;

import tiei.ajp.project.model.ParkingSession;

import java.util.List;
import java.util.Optional;

/** Defines how the service saves and retrieves parking sessions. */
public interface ParkingSessionRepository {
    long nextId();

    /** Inserts a new session or updates an existing session by ID. */
    void save(ParkingSession session);

    Optional<ParkingSession> findById(long sessionId);

    List<ParkingSession> findAll();
}
