package geodes.sms.astparser;

import geodes.sms.astparser.graph.IdentifierNode;

public class Identifier {
    private final String id;

    private IdentifierNode node;

    Identifier(String value) {
        id = value;
    }

    public String getId() { return id; }

    public void setNode(IdentifierNode node) { this.node = node; }

    public IdentifierNode getNode() { return node; }

    public String toString() {
        if (node != null)
            return String.format("%s (%s)", id, node);
        return id;
    }
}
