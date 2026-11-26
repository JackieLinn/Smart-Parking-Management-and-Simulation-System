package tiei.ajp.project.model;

/**
 * 可接收错停派单的巡逻员。空闲状态由尚未完成的派单决定。
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
