package geodes.sms.astparser.graph;

public class IdentifierNode {
    private final String label;

    private String feature;

    public IdentifierNode(String label, String feature) {
        this.label = label;
        this.feature = feature;
    }

    IdentifierNode(String label) {
        this.label = label;
    }

    public boolean equals(Object o) {
        if (o instanceof IdentifierNode) {
            IdentifierNode other = (IdentifierNode) o;
            return label.equals(other.label) && feature.equals(other.feature);
        }
        return false;
    }

    public String getLabel() {
        return label;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) { this.feature = feature; }

    public String toString() {
        return String.format("%s / %s", label, feature);
    }
}
