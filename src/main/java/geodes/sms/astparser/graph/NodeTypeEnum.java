package geodes.sms.astparser.graph;

public enum NodeTypeEnum {
    ROOT(0),
    PARAM(1),
    IMPORT(2),
    VAR(3),
    CALL(4),
    ID(5);

    private final int id;

    private NodeTypeEnum(int id) { this.id = id; }

    public int getId() { return id; }
}
