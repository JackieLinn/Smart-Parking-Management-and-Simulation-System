/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

import java.time.Instant;

/**
 * Records one sensor-confirmed occupancy period. Parking in another space starts a new period.
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
