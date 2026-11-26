package tiei.ajp.project.model;

import java.time.Instant;

/**
 * 告警派给谁、何时交接，以及巡逻员何时可以接下一单。
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
