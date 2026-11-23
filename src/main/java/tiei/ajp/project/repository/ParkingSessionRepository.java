package tiei.ajp.project.repository;

import tiei.ajp.project.model.ParkingSession;

import java.util.List;
import java.util.Optional;

/** 约定停车会话的保存和查询方式，业务层通过此接口访问数据。 */
public interface ParkingSessionRepository {
    long nextId();

    /** 新会话按 ID 添加，已有会话按同一个 ID 更新。 */
    void save(ParkingSession session);

    Optional<ParkingSession> findById(long sessionId);

    List<ParkingSession> findAll();
}
