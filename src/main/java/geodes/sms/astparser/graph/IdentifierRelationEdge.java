package geodes.sms.astparser.graph;

public class IdentifierRelationEdge {
    private final IdentifierNode sourceNode;

    private final IdentifierNode targetNode;

    private String value;

    IdentifierRelationEdge(String value, IdentifierNode sourceNode, IdentifierNode targetNode) {
        this.value = value;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String toString() {
        return String.format("%s --%s-> %s", sourceNode.getLabel(), value, targetNode.getLabel());
    }

    public IdentifierNode getTargetNode() {
        return targetNode;
    }

    public IdentifierNode getSourceNode() {
        return sourceNode;
    }
}
