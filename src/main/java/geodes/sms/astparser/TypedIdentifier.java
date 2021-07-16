package geodes.sms.astparser;

import geodes.sms.astparser.graph.IdentifierNode;

public class TypedIdentifier extends Identifier {
    private final String type;

    private IdentifierNode typeNode;

    TypedIdentifier(String value, String type) {
        super(value);
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public IdentifierNode getTypeNode() {
        return typeNode;
    }

    public void setTypeNode(IdentifierNode typeNode) {
        this.typeNode = typeNode;
    }

    public String toString() {
        return String.format("%s (type: %s)", this.getId(), type);
    }
}
