/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

/**
 * Represents a patrol officer who is available when they have no active assignment.
 */
public class PatrolOfficer {
    private final long id;
    private final String code;
    private final String name;

    public PatrolOfficer(long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }
}
