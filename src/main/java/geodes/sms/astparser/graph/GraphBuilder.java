package geodes.sms.astparser.graph;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import java.util.Optional;
import java.util.logging.Logger;

public class GraphBuilder {
    /**
     *  Representation of a graph, i.e., a mutable network.
     *  Each node is represented by the IdentifierNode abstract type and contains a label and a feature.
     *  Each edge is directed and has a source node and a target node.
     *  The edges all have a label which represents the relation between two identifiers.
     */
    private final MutableNetwork<IdentifierNode, IdentifierRelationEdge> graph;

    private final int rootNodeId;

    private int nodeCounter = 0;

    private final Logger logger;

    public GraphBuilder(String methodName, int hash) {
        logger = Logger.getLogger(GraphBuilder.class.getName());
        logger.fine("Building graph for method: " + methodName);

        graph = NetworkBuilder.directed()
                .allowsSelfLoops(true)
                .allowsParallelEdges(true)
                .build();
        graph.addNode(new IdentifierNode(hash, nodeCounter, methodName, NodeTypeEnum.METHOD.name()));
        nodeCounter++;
        rootNodeId = hash;
    }

    public Optional<IdentifierNode> getNode(int hash) {
        return graph.nodes().stream()
                .filter(n -> n.getId() == hash)
                .findAny();
    }

    public boolean nodeExists(int hash) {
        return graph.nodes().stream().anyMatch(n -> n.getId() == hash);
    }

    public void addNode(int hash, String name, String type) {
        if (!nodeExists(hash)) {
            graph.addNode(new IdentifierNode(hash, nodeCounter, name, type));
            nodeCounter++;
        }
    }

    public boolean edgeExists(IdentifierNode sourceNode, IdentifierNode targetNode, String type) {
        return graph.edgesConnecting(sourceNode, targetNode).stream().anyMatch(r -> r.getValue().equals(type));
    }

    public void addEdge(String relation, IdentifierNode source, IdentifierNode target) {
        if (!edgeExists(source, target, relation)) {
            graph.addEdge(source, target, new IdentifierRelationEdge(relation, source, target));
        }
    }

    public void checkUnconnectedNodes() {
        Optional<IdentifierNode> method = getNode(rootNodeId);
        if (method.isPresent()) {
            graph.nodes().forEach(n -> {
                if (graph.inEdges(n).isEmpty() && graph.outEdges(n).isEmpty()) {
                    addEdge(EdgeTypeEnum.CONTAINS.name(), method.get(), n);
                }
            });
        }
    }

    public MutableNetwork<IdentifierNode, IdentifierRelationEdge> getGraph() {
        return graph;
    }

    public String printGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("graph {\n");
        graph.nodes().forEach(n -> sb.append(String.format("\tnode: %s (id: %s)\n", n, n.getId())));
        graph.edges().forEach(e -> sb.append(String.format("\n\tedge: %s", e)));
        sb.append("\n}\n");
        return sb.toString();
    }

    public String toString() { return printGraph(); }
}
