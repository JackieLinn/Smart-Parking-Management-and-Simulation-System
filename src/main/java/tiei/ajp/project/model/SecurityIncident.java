package tiei.ajp.project.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次尚未纠正的错停可以经历多个车位，期间沿用同一派单和截止时间。
 */
public class SecurityIncident {
    public enum Status {QUEUED, ASSIGNED, NOTIFIED, REVIEW_REQUIRED, RESOLVED, CLOSED_UNCORRECTED}

    private final long id;
    private final long sessionId;
    private final long assignedSpaceId;
    private final Instant detectedAt;
    private final List<PatrolMessage> messages = new ArrayList<>();
    private long lastWrongSpaceId;
    private PatrolAssignment assignment;
    private Instant moveDeadline;
    private Instant closedAt;
    private boolean corrected;
    private boolean reviewRequired;
    private boolean reviewed;
    private Fine fine;

    public SecurityIncident(long id, long sessionId, long assignedSpaceId, long wrongSpaceId, Instant detectedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.assignedSpaceId = assignedSpaceId;
        this.lastWrongSpaceId = wrongSpaceId;
        this.detectedAt = Objects.requireNonNull(detectedAt);
    }

    public void assign(long officerId, Instant at) {
        if (status() != Status.QUEUED || at.isBefore(detectedAt)) {
            throw new IllegalStateException("Incident is not ready for dispatch");
        }
        assignment = new PatrolAssignment(officerId, at);
    }

    public void report(PatrolMessage message, Duration gracePeriod) {
        if (!isOpen() || assignment == null || assignment.officerId() != message.officerId()) {
            throw new IllegalStateException("Officer does not have an active assignment for this incident");
        }
        // 同类反馈只接受一次，重复通知不会重置移车截止时间。
        if (messages.stream().anyMatch(previous -> previous.type() == message.type())) {
            return;
        }
        if (assignment.completedAt() != null) {
            throw new IllegalStateException("Patrol assignment is already complete");
        }
        Instant previous = messages.isEmpty() ? assignment.assignedAt() : messages.getLast().sentAt();
        if (message.sentAt().isBefore(previous)) {
            throw new IllegalArgumentException("Patrol report precedes the previous action");
        }
        if (message.type() == PatrolMessage.Type.DRIVER_NOTIFIED) {
            moveDeadline = message.sentAt().plus(gracePeriod);
            assignment.complete(message.sentAt());
        }
        messages.add(message);
    }

    public void observeAnotherWrongSpace(long spaceId) {
        lastWrongSpaceId = spaceId;
    }

    public boolean allowsAutomaticFine() {
        return isOpen() && moveDeadline != null && !reviewRequired && !reviewed;
    }

    public void issueFine(Fine.Reason reason, BigDecimal amount, Instant at) {
        if (fine == null) {
            fine = new Fine(id, reason, amount, at);
        }
    }

    public void requestReview() {
        if (!isOpen()) {
            throw new IllegalStateException("Incident has already closed");
        }
        reviewRequired = true;
    }

    public void completeReview(boolean upholdFine, BigDecimal amount, Instant at) {
        if (!reviewRequired) {
            throw new IllegalStateException("Incident is not awaiting review");
        }
        if (upholdFine) {
            issueFine(Fine.Reason.CONFIRMED_BY_REVIEW, amount, at);
        } else {
            fine = null;
        }
        reviewRequired = false;
        reviewed = true;
    }

    public void close(boolean corrected, Instant at) {
        this.corrected = corrected;
        closedAt = at;
        if (assignment != null) {
            assignment.complete(at);
        }
    }

    public Status status() {
        if (reviewRequired) {
            return Status.REVIEW_REQUIRED;
        }
        if (!isOpen()) {
            return corrected ? Status.RESOLVED : Status.CLOSED_UNCORRECTED;
        }
        if (moveDeadline != null) {
            return Status.NOTIFIED;
        }
        return assignment == null ? Status.QUEUED : Status.ASSIGNED;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public long id() {
        return id;
    }

    public long sessionId() {
        return sessionId;
    }

    public long assignedSpaceId() {
        return assignedSpaceId;
    }

    public long lastWrongSpaceId() {
        return lastWrongSpaceId;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public PatrolAssignment assignment() {
        return assignment;
    }

    public List<PatrolMessage> messages() {
        return List.copyOf(messages);
    }

    public Instant moveDeadline() {
        return moveDeadline;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public Fine fine() {
        return fine;
    }
}
