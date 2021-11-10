package geodes.sms.astparser.graph;

public class IdentifierNode {
    private final int id;

    private final int idCount;

    private final String label;

    private final String type;

    public IdentifierNode(int id, int count, String label, String feature) {
        this.id = id;
        this.idCount = count;
        this.label = label;
        this.type = feature;
    }

    public boolean equals(Object o) {
        if (o instanceof IdentifierNode) {
            IdentifierNode other = (IdentifierNode) o;
            return label.equals(other.label) && type.equals(other.type);
        }
        return false;
    }

    public int getId() { return id; }

    public int getIdCount() { return idCount; }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public String toString() {
        return String.format("%s / %s", label, type);
    }
}
