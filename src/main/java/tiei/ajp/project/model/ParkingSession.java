/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks one vehicle's parking visit from space allocation to confirmed exit.
 */
public class ParkingSession {
    public enum Status {
        RESERVED,   // Space reserved, awaiting entry
        ENTERED,    // Vehicle has entered and is driving to its space
        PARKED,     // Vehicle has parked, so billing starts
        LEFT_SPACE, // Vehicle has left the space and may repark or head to the exit
        AT_EXIT,    // Vehicle has reached the exit and is awaiting payment
        PAID,       // Payment confirmed, awaiting exit
        EXITED      // Vehicle has exited and the session is complete
    }

    private final long id;
    private final String plate;
    private final long spaceId;      // ID of the assigned space
    private final Instant reservedAt;
    private final List<SpaceOccupancy> occupancies = new ArrayList<>();
    private Status status = Status.RESERVED;
    private boolean assignedSpaceReleased;
    // An Instant records when an event occurred. The field is null until the event happens.
    private Instant enteredAt;       // Time the vehicle passed the entrance barrier
    private Instant arrivedAtExit;
    private Instant paidAt;          // Time the simulated payment was confirmed
    private Instant exitedAt;        // Time the vehicle passed the exit barrier

    public ParkingSession(long id, String plate, long spaceId, Instant reservedAt) {
        this.id = id;
        this.plate = Objects.requireNonNull(plate);
        this.spaceId = spaceId;
        this.reservedAt = Objects.requireNonNull(reservedAt);
    }

    public void confirmEntry(Instant at) {
        checkEvent(Status.RESERVED, reservedAt, at);
        enteredAt = at;
        status = Status.ENTERED;
    }

    public void confirmParking(long observedSpaceId, Instant at) {
        confirmParking(observedSpaceId, at, observedSpaceId == spaceId);
    }

    public void confirmParking(long observedSpaceId, Instant at, boolean correctlyParked) {
        if (correctlyParked && observedSpaceId != spaceId) {
            throw new IllegalArgumentException("Correct parking must be in the assigned space");
        }
        if (status == Status.LEFT_SPACE) {
            checkEvent(Status.LEFT_SPACE, leftSpaceAt(), at);
        } else {
            checkEvent(Status.ENTERED, enteredAt, at);
        }
        occupancies.add(new SpaceOccupancy(observedSpaceId, at, correctlyParked));
        status = Status.PARKED;
    }

    public void confirmSpaceDeparture(Instant at) {
        SpaceOccupancy occupancy = lastOccupancy();
        checkEvent(Status.PARKED, occupancy == null ? null : occupancy.parkedAt(), at);
        occupancy.leave(at);
        // After leaving the wrong space, the assigned space stays reserved until the vehicle leaves it or exits the facility.
        if (occupancy.correctlyParked()) {
            assignedSpaceReleased = true;
        }
        status = Status.LEFT_SPACE;
    }

    public void confirmExitArrival(Instant at) {
        checkEvent(Status.LEFT_SPACE, leftSpaceAt(), at);
        arrivedAtExit = at;
        status = Status.AT_EXIT;
    }

    public void confirmPayment(Instant at) {
        if (status == Status.LEFT_SPACE) {
            confirmExitArrival(at);
        }
        checkEvent(Status.AT_EXIT, arrivedAtExit, at);
        paidAt = at;
        status = Status.PAID;
    }

    public void confirmExit(Instant at) {
        checkEvent(Status.PAID, paidAt, at);
        exitedAt = at;
        status = Status.EXITED;
    }

    private void checkEvent(Status expected, Instant previous, Instant at) {
        // Each event requires a prior state and a timestamp no earlier than the previous event.
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but session is " + status);
        }
        if (at.isBefore(previous)) {
            throw new IllegalArgumentException("Event time precedes the previous event");
        }
    }

    /**
     * Reports whether the assigned space is still reserved for this session.
     */
    public boolean holdsSpace() {
        return !assignedSpaceReleased && status != Status.EXITED;
    }

    /**
     * Hold a released assignment again once the service confirms the space is still available.
     */
    public void retainAssignedSpace() {
        assignedSpaceReleased = false;
    }

    /**
     * Sums completed occupancy periods and excludes time spent driving between spaces.
     */
    public Duration billableDuration() {
        if (leftSpaceAt() == null || status == Status.PARKED) {
            throw new IllegalStateException("Vehicle has not left the parking space");
        }
        Duration total = Duration.ZERO;
        for (SpaceOccupancy occupancy : occupancies) {
            total = total.plus(Duration.between(occupancy.parkedAt(), occupancy.leftAt()));
        }
        return total;
    }

    public List<SpaceOccupancy> occupancies() {
        return List.copyOf(occupancies);
    }

    public boolean occupiesSpace(long observedSpaceId) {
        return status == Status.PARKED && lastOccupancy().spaceId() == observedSpaceId;
    }

    public SpaceOccupancy lastOccupancy() {
        return occupancies.isEmpty() ? null : occupancies.getLast();
    }

    public long id() {
        return id;
    }

    public String plate() {
        return plate;
    }

    public long spaceId() {
        return spaceId;
    }

    public Status status() {
        return status;
    }

    public Instant enteredAt() {
        return enteredAt;
    }

    public Instant parkedAt() {
        return occupancies.isEmpty() ? null : occupancies.getFirst().parkedAt();
    }

    public Instant leftSpaceAt() {
        return lastOccupancy() == null ? null : lastOccupancy().leftAt();
    }

    public Instant exitedAt() {
        return exitedAt;
    }

    public Instant arrivedAtExit() {
        return arrivedAtExit;
    }
}
