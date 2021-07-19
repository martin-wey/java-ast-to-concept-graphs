package geodes.sms.astparser.graph;

public class IdentifierNode {
    private final int id;

    private final String label;

    private final String feature;

    public IdentifierNode(int id, String label, String feature) {
        this.id = id;
        this.label = label;
        this.feature = feature;
    }

    public boolean equals(Object o) {
        if (o instanceof IdentifierNode) {
            IdentifierNode other = (IdentifierNode) o;
            return label.equals(other.label) && feature.equals(other.feature);
        }
        return false;
    }

    public int getId() { return id; }

    public String getLabel() {
        return label;
    }

    public String getFeature() {
        return feature;
    }

    public String toString() {
        return String.format("%s / %s", label, feature);
    }
}
