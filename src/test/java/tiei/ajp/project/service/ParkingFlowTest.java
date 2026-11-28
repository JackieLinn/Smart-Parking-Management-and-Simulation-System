/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.service;

import org.junit.jupiter.api.Test;
import tiei.ajp.project.model.ParkingSession;
import tiei.ajp.project.model.ParkingSpace;
import tiei.ajp.project.model.ParkingUser;
import tiei.ajp.project.model.UserType;
import tiei.ajp.project.repository.InMemoryParkingSessionRepository;
import tiei.ajp.project.repository.ParkingSessionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingFlowTest {
    @Test
    void vehicleReservesParksPaysAndExits() {
        // Set up two spaces and use fixed timestamps to simulate device events.
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
        assertNull(reserved.userId());
        assertEquals(UserType.OCCASIONAL, reserved.userType());

        // The first space is reserved, so the next vehicle gets the second space.
        ParkingUser subscriber = new ParkingUser(201L, "Demo Subscriber", UserType.SUBSCRIBER);
        long otherId = parking.reserveSpace("DEMO-456", subscriber, enteredAt);
        ParkingSession otherSession = sessions.findById(otherId).orElseThrow();
        assertEquals(secondSpace.id(), otherSession.spaceId());
        assertEquals(subscriber.id(), otherSession.userId());
        assertEquals(UserType.SUBSCRIBER, otherSession.userType());

        parking.confirmEntry(sessionId, enteredAt);
        parking.confirmParking(sessionId, firstSpace.id(), parkedAt);
        assertThrows(IllegalStateException.class, () -> parking.reserveSpace("DEMO-789", parkedAt));

        // Leaving the space stops billing and releases the space while the vehicle is still in the facility.
        parking.confirmSpaceDeparture(sessionId, leftSpaceAt);
        ParkingSession departed = sessions.findById(sessionId).orElseThrow();
        assertEquals(Duration.ofMinutes(115), departed.billableDuration());
        assertEquals(ParkingSession.Status.LEFT_SPACE, departed.status());
        long nextId = parking.reserveSpace("DEMO-789", leftSpaceAt);
        assertEquals(firstSpace.id(), sessions.findById(nextId).orElseThrow().spaceId());

        // Simulate payment, confirm exit, and check all four recorded timestamps.
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
