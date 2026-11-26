package tiei.ajp.project.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一条错停告警最多产生一笔罚款。
 */
public class Fine {
    public enum Reason {MOVE_DEADLINE_EXCEEDED, EXIT_WITHOUT_CORRECTION, CONFIRMED_BY_REVIEW}

    private final long incidentId;
    private final Reason reason;
    private final BigDecimal amount;
    private final Instant issuedAt;

    public Fine(long incidentId, Reason reason, BigDecimal amount, Instant issuedAt) {
        this.incidentId = incidentId;
        this.reason = reason;
        this.amount = amount;
        this.issuedAt = issuedAt;
    }

    public long incidentId() {
        return incidentId;
    }

    public Reason reason() {
        return reason;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Instant issuedAt() {
        return issuedAt;
    }
}
