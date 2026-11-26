/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Records a patrol report. The {@code DRIVER_NOTIFIED} type confirms the officer asked the driver to move.
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
