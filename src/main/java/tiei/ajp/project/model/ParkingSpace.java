/*
 * Group members
 * 2026248015 Lin Xiaoyi
 * 2026248027 Ren Zongxuan
 * 2026248068 Tang Chuanyue
 */

package tiei.ajp.project.model;

/** Identifies a parking space by its numeric ID and a display code such as B-X9. */
public class ParkingSpace {
    private final long id;
    private final String code;

    public ParkingSpace(long id, String code) {
        this.id = id;
        this.code = code;
    }

    public long id() {
        return id;
    }

    public String code() {
        return code;
    }
}
