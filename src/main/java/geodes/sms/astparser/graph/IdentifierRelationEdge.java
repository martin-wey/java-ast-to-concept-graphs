package geodes.sms.astparser.graph;

public class IdentifierRelationEdge {
    private final int id;

    private final IdentifierNode sourceNode;

    private final IdentifierNode targetNode;

    private final String value;

    IdentifierRelationEdge(int id, String value, IdentifierNode sourceNode, IdentifierNode targetNode) {
        this.id = id;
        this.value = value;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public int getId() { return id; }

    public String getValue() {
        return value;
    }

    public IdentifierNode getSourceNode() { return sourceNode; }

    public IdentifierNode getTargetNode() { return targetNode; }

    public String toString() {
        return String.format("%s --%s-> %s", sourceNode.getLabel(), value, targetNode.getLabel());
    }
}
