package tiei.ajp.project.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 记录一辆车从分配车位到实际出场的一次停车过程。
 */
public class ParkingSession {
    public enum Status {
        RESERVED,   // 车位已预留，等待车辆入场
        ENTERED,    // 已通过入口，正在开往车位
        PARKED,     // 已停入车位，开始计费
        LEFT_SPACE, // 已离开车位，可以移车或开往出口
        AT_EXIT,    // 已到出口，等待结算
        PAID,       // 已付款，等待车辆通过出口
        EXITED      // 已通过出口，本次停车结束
    }

    private final long id;
    private final String plate;
    private final long spaceId;      // 系统分配的车位 ID
    private final Instant reservedAt;
    private final List<SpaceOccupancy> occupancies = new ArrayList<>();
    private Status status = Status.RESERVED;
    private boolean assignedSpaceReleased;
    // Instant 表示具体时刻。对应事件发生前，字段值为 null。
    private Instant enteredAt;       // 实际通过入口
    private Instant arrivedAtExit;
    private Instant paidAt;          // 模拟付款确认
    private Instant exitedAt;        // 实际通过出口

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
        // 错停离位只结束实占，原分配位继续保留，直到正确停车后离位或出场。
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
        // 每个事件都有对应的前置状态，时间也必须按顺序推进。
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but session is " + status);
        }
        if (at.isBefore(previous)) {
            throw new IllegalArgumentException("Event time precedes the previous event");
        }
    }

    /**
     * 从预留开始保留车位，车辆离位后即可分配给下一辆车。
     */
    public boolean holdsSpace() {
        return !assignedSpaceReleased && status != Status.EXITED;
    }

    /**
     * 已经释放的分配位只有在服务确认仍可用时才能重新保留。
     */
    public void retainAssignedSpace() {
        assignedSpaceReleased = false;
    }

    /**
     * 累加每段已经结束的实际占用，移车途中不计费。
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
