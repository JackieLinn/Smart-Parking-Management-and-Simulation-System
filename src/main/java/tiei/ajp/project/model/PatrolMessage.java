package tiei.ajp.project.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 巡逻员反馈，只有 DRIVER_NOTIFIED 表示已把移车要求告知司机。
 */
public class PatrolMessage {
    public enum Type {ARRIVED, DRIVER_NOTIFIED}

    private final long officerId;
    private final Type type;
    private final String text;
    private final Instant sentAt;

    public PatrolMessage(long officerId, Type type, String text, Instant sentAt) {
        this.officerId = officerId;
        this.type = Objects.requireNonNull(type);
        this.text = Objects.requireNonNull(text);
        this.sentAt = Objects.requireNonNull(sentAt);
    }

    public long officerId() {
        return officerId;
    }

    public Type type() {
        return type;
    }

    public String text() {
        return text;
    }

    public Instant sentAt() {
        return sentAt;
    }
}
