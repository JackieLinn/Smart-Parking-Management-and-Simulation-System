package tiei.ajp.project.model;

/** 车位的固定资料。业务逻辑用数字 ID 关联，界面可显示 B-X9 这样的编号。 */
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
