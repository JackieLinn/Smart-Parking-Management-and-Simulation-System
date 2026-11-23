package tiei.ajp.project.service;

import org.junit.jupiter.api.Test;
import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
import tiei.ajp.project.repository.InMemoryParkingSessionRepository;
import tiei.ajp.project.repository.ParkingSessionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingFlowTest {
    @Test
    void vehicleReservesParksPaysAndExits() {
        // 准备两个车位，用固定时间模拟设备上报的事件。
        ParkingSpace firstSpace = new ParkingSpace(101L, "B-X9");
        ParkingSpace secondSpace = new ParkingSpace(102L, "B-X10");
        ParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
        ParkingService parking = new ParkingService(List.of(firstSpace, secondSpace), sessions);

        Instant enteredAt = Instant.parse("2026-11-23T00:00:00Z");
        Instant parkedAt = enteredAt.plus(Duration.ofMinutes(5));
        Instant leftSpaceAt = enteredAt.plus(Duration.ofHours(2));
        Instant exitedAt = leftSpaceAt.plus(Duration.ofMinutes(5));

        long sessionId = parking.reserveSpace("DEMO-123", enteredAt);
        ParkingSession reserved = sessions.findById(sessionId).orElseThrow();
        assertEquals(firstSpace.id(), reserved.spaceId());
        assertEquals(ParkingSession.Status.RESERVED, reserved.status());

        // 第一个车位已预留，下一辆车只能分到第二个车位。
        long otherId = parking.reserveSpace("DEMO-456", enteredAt);
        assertEquals(secondSpace.id(), sessions.findById(otherId).orElseThrow().spaceId());

        parking.confirmEntry(sessionId, enteredAt);
        parking.confirmParking(sessionId, firstSpace.id(), parkedAt);
        assertThrows(IllegalStateException.class, () -> parking.reserveSpace("DEMO-789", parkedAt));

        // 离位即停止计费并释放车位，此时车辆仍在场内。
        parking.confirmSpaceDeparture(sessionId, leftSpaceAt);
        ParkingSession departed = sessions.findById(sessionId).orElseThrow();
        assertEquals(Duration.ofMinutes(115), departed.billableDuration());
        assertEquals(ParkingSession.Status.LEFT_SPACE, departed.status());
        long nextId = parking.reserveSpace("DEMO-789", leftSpaceAt);
        assertEquals(firstSpace.id(), sessions.findById(nextId).orElseThrow().spaceId());

        // 模拟付款后再确认出场，最后检查会话保存的四个时间点。
        assertThrows(IllegalStateException.class, () -> parking.confirmExit(sessionId, exitedAt));
        parking.confirmPayment(sessionId, exitedAt);
        assertEquals(ParkingSession.Status.PAID, sessions.findById(sessionId).orElseThrow().status());
        parking.confirmExit(sessionId, exitedAt);

        ParkingSession completed = sessions.findById(sessionId).orElseThrow();
        assertEquals(ParkingSession.Status.EXITED, completed.status());
        assertEquals(enteredAt, completed.enteredAt());
        assertEquals(parkedAt, completed.parkedAt());
        assertEquals(leftSpaceAt, completed.leftSpaceAt());
        assertEquals(exitedAt, completed.exitedAt());
    }
}
