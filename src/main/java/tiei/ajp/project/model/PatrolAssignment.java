/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

import java.time.Instant;

/**
 * Records the assigned officer, dispatch time, and when the officer becomes available again.
 */
public class PatrolAssignment {
    private final long officerId;
    private final Instant assignedAt;
    private Instant completedAt;

    PatrolAssignment(long officerId, Instant assignedAt) {
        this.officerId = officerId;
        this.assignedAt = assignedAt;
    }

    void complete(Instant at) {
        if (completedAt == null) {
            completedAt = at;
        }
    }

    public long officerId() {
        return officerId;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
