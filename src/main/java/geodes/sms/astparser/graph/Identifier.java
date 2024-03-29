package geodes.sms.astparser.graph;

public class Identifier {
    private final String id;

    private IdentifierNode node;

    public Identifier(String value) {
        id = value;
    }

    public void setNode(IdentifierNode node) { this.node = node; }

    public String toString() {
        if (node != null)
            return String.format("%s (%s)", id, node);
        return id;
    }
}
