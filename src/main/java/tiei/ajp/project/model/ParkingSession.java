package tiei.ajp.project.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 记录一辆车从分配车位到实际出场的一次停车过程。 */
public class ParkingSession {
    public enum Status {
        RESERVED,   // 车位已预留，等待车辆入场
        ENTERED,    // 已通过入口，正在开往车位
        PARKED,     // 已停入车位，开始计费
        LEFT_SPACE, // 已离开车位，计费结束，等待付款
        PAID,       // 已付款，等待车辆通过出口
        EXITED      // 已通过出口，本次停车结束
    }

    private final long id;
    private final String plate;
    private final long spaceId;      // 系统分配的车位 ID
    private final Instant reservedAt;
    private Status status = Status.RESERVED;
    // Instant 表示具体时刻。对应事件发生前，字段值为 null。
    private Instant enteredAt;       // 实际通过入口
    private Instant parkedAt;        // 实际停入，计费开始
    private Instant leftSpaceAt;     // 实际离位，计费结束
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
        checkEvent(Status.ENTERED, enteredAt, at);
        if (observedSpaceId != spaceId) {
            throw new IllegalArgumentException("Vehicle must park in its assigned space");
        }
        parkedAt = at;
        status = Status.PARKED;
    }

    public void confirmSpaceDeparture(Instant at) {
        checkEvent(Status.PARKED, parkedAt, at);
        leftSpaceAt = at;
        status = Status.LEFT_SPACE;
    }

    public void confirmPayment(Instant at) {
        checkEvent(Status.LEFT_SPACE, leftSpaceAt, at);
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

    /** 从预留开始保留车位，车辆离位后即可分配给下一辆车。 */
    public boolean holdsSpace() {
        return switch (status) {
            case RESERVED, ENTERED, PARKED -> true;
            case LEFT_SPACE, PAID, EXITED -> false;
        };
    }

    /** 收费时长只取停入到离位之间的时间。 */
    public Duration billableDuration() {
        if (leftSpaceAt == null) {
            throw new IllegalStateException("Vehicle has not left the parking space");
        }
        return Duration.between(parkedAt, leftSpaceAt);
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
        return parkedAt;
    }

    public Instant leftSpaceAt() {
        return leftSpaceAt;
    }

    public Instant exitedAt() {
        return exitedAt;
    }
}
