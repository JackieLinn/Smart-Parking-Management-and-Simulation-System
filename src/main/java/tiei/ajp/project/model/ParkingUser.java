/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

import java.util.Objects;

/**
 * A parking account for a person or a transport company.
 */
public class ParkingUser {
    private final long id;
    private final String displayName;
    private final UserType type;

    public ParkingUser(long id, String displayName, UserType type) {
        this.id = id;
        this.displayName = Objects.requireNonNull(displayName, "Display name is required").strip();
        if (this.displayName.isEmpty()) {
            throw new IllegalArgumentException("Display name is required");
        }
        this.type = Objects.requireNonNull(type, "User type is required");
    }

    public long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public UserType type() {
        return type;
    }
}
