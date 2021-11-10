package geodes.sms.astparser.graph;

public enum NodeTypeEnum {
    METHOD(0),
    PARAM(1),
    IMPORT(2),
    VAR(3),
    CALL(4),
    ID(5);

    private final int id;

    NodeTypeEnum(int id) { this.id = id; }

    public int getId() { return id; }
}
