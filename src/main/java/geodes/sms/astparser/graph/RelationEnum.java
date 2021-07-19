package geodes.sms.astparser.graph;

public enum RelationEnum {
    DEPENDS_ON(0),
    PARAMETER(1),
    TYPE(2),
    DEFINES(3),
    CALLS(4),
    SCOPE(5),
    ARG(6),
    RELATED_TO(7),
    CONTAINS(8);

    private final int id;

    private RelationEnum(int id) { this.id = id; }

    public int getId() { return id; }
}
