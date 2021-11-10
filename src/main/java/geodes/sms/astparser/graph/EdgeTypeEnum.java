package geodes.sms.astparser.graph;

public enum EdgeTypeEnum {
    DEPENDS_ON(0),
    PARAMETER(1),
    TYPE(2),
    DEFINES(3),
    SCOPE(4),
    ARG(5),
    CONTAINS(6);

    private final int id;

    EdgeTypeEnum(int id) { this.id = id; }

    public int getId() { return id; }
}
