package tiei.ajp.project.model;

import java.time.Instant;

/**
 * 传感器确认的一段实际占用，移车后会产生新的记录。
 */
public class SpaceOccupancy {
    private final long spaceId;
    private final Instant parkedAt;
    private final boolean correctlyParked;
    private Instant leftAt;

    SpaceOccupancy(long spaceId, Instant parkedAt, boolean correctlyParked) {
        this.spaceId = spaceId;
        this.parkedAt = parkedAt;
        this.correctlyParked = correctlyParked;
    }

    void leave(Instant at) {
        leftAt = at;
    }

    public long spaceId() {
        return spaceId;
    }

    public Instant parkedAt() {
        return parkedAt;
    }

    public Instant leftAt() {
        return leftAt;
    }

    public boolean correctlyParked() {
        return correctlyParked;
    }
}
