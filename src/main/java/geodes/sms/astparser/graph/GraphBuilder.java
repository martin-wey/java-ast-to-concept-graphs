package geodes.sms.astparser.graph;

import com.github.javaparser.utils.Pair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GraphBuilder {
    /**
     *  Representation of a graph, i.e., a mutable network.
     *  Each node is represented by the IdentifierNode abstract type and contains a label and a feature.
     *  Each edge is directed and has a source node and a target node.
     *  The edges all have a label which represents the relation between two identifiers.
     */
    private final MutableNetwork<IdentifierNode, IdentifierRelationEdge> graph;

    private final Logger logger;

    public GraphBuilder(IdentifierNode methodNode) {
        logger = Logger.getLogger(GraphBuilder.class.getName());
        logger.info("Building graph for method: " + methodNode.getLabel());

        graph = NetworkBuilder.directed()
                .allowsParallelEdges(true)
                .build();
        // the root node is the method node
        graph.addNode(methodNode);
    }

    /**
     * Add exceptions thrown by the method to the graph.
     *
     * @param exceptions list of exception identifiers.
     */
    public void setExceptionNodes(List<String> exceptions) {
        exceptions.forEach(e -> {
            IdentifierNode exceptionNode = new IdentifierNode(e, "import");
            graph.addNode(exceptionNode);
            // the method depends on the exception which is imported
            graph.addEdge(getMethodNode(), exceptionNode, new IdentifierRelationEdge("dependsOn", getMethodNode(), exceptionNode));
        });
    }

    /**
     * Add method parameters to the graph.
     *
     * @param parameters list of parameter identifiers with their corresponding types.
     */
    public void setParameterNodes(List<Pair<String, String>> parameters) {
        parameters.forEach(p -> {
            IdentifierNode paramIdNode = new IdentifierNode(p.a, "param");
            graph.addNode(paramIdNode);
            // relation: method --param->
            graph.addEdge(getMethodNode(), paramIdNode, new IdentifierRelationEdge("parameter", getMethodNode(), paramIdNode));
            if (p.b != null && !nodeAndFeatureInGraph(p.b, "import")) {
                // the type is an imported abstract type -> feature = import
                IdentifierNode paramTypeNode = new IdentifierNode(p.b, "import");
                graph.addNode(paramTypeNode);
                // relation: method --dependsOn->
                graph.addEdge(getMethodNode(), paramTypeNode, new IdentifierRelationEdge("dependsOn", getMethodNode(), paramTypeNode));
                // relation: parameter --type->
                graph.addEdge(paramIdNode, paramTypeNode, new IdentifierRelationEdge("type", paramIdNode, paramTypeNode));
            } else if (p.b != null && nodeAndFeatureInGraph(p.b, "import")) {
                Optional<IdentifierNode> varTypeNode = getNodeWithFeature(p.b, "import");
                varTypeNode.ifPresent(node ->
                    graph.addEdge(paramIdNode, node, new IdentifierRelationEdge("type", paramIdNode, node))
                );
            }
        });
    }

    /**
     * Add variables declared in the method body to the graph.
     *
     * @param variables list of pairs of string containing the variable name and its type.
     */
    public void setVariableNodes(List<Pair<String, String>> variables) {
        if (variables != null) {
            variables.forEach(v -> {
                if (!nodeAndFeatureInGraph(v.a, "var")) {
                    IdentifierNode varIdNode = new IdentifierNode(v.a, "var");
                    graph.addNode(varIdNode);
                    if (v.b != null && !nodeAndFeatureInGraph(v.b, "import")) {
                        IdentifierNode varTypeNode = new IdentifierNode(v.b, "import");
                        graph.addNode(varTypeNode);
                        graph.addEdge(getMethodNode(), varTypeNode, new IdentifierRelationEdge("dependsOn", getMethodNode(), varTypeNode));
                        graph.addEdge(varIdNode, varTypeNode, new IdentifierRelationEdge("type", varIdNode, varTypeNode));
                    } else if (v.b != null && nodeAndFeatureInGraph(v.b, "import")) {
                        Optional<IdentifierNode> varTypeNode = getNodeWithFeature(v.b, "import");
                        varTypeNode.ifPresent(typeNode ->
                                graph.addEdge(varIdNode, typeNode, new IdentifierRelationEdge("type", varIdNode, typeNode))
                        );
                    }
                    graph.addEdge(getMethodNode(), varIdNode, new IdentifierRelationEdge("defines", getMethodNode(), varIdNode));
                }
            });
        }
    }

    /**
     * Add variable casting relations.
     *
     * @param casts list of pairs of string containing the variable name and its casting type.
     */
    public void setVariableCasts(List<Pair<String, String>> casts) {
        if (casts != null) {
            casts.stream().filter(Objects::nonNull).forEach(c -> {
                Optional<IdentifierNode> var = getNodeWithFeature(c.a, "var");
                IdentifierNode varNode;
                if (var.isPresent()) {
                    varNode = var.get();
                } else {
                    varNode = new IdentifierNode(c.a, "var");
                    graph.addNode(varNode);
                    graph.addEdge(getMethodNode(), varNode, new IdentifierRelationEdge("defines", getMethodNode(), varNode));
                }

                Optional<IdentifierNode> cast = getNodeWithFeature(c.b, "import");
                IdentifierNode castNode;
                if (cast.isPresent()) {
                    castNode = cast.get();
                } else {
                    castNode = new IdentifierNode(c.b, "import");
                    graph.addNode(castNode);
                    graph.addEdge(getMethodNode(), castNode, new IdentifierRelationEdge("dependsOn", getMethodNode(), castNode));
                }
                graph.addEdge(varNode, castNode, new IdentifierRelationEdge("type", varNode, castNode));
            });
        }
    }

    /**
     * Add method call nodes in the graph.
     *
     * @param calls list of method call names.
     */
    public void setCallNodes(List<String> calls) {
        if (calls != null) {
            calls.forEach(c -> {
                IdentifierNode callNode = new IdentifierNode(c, "call");
                graph.addNode(callNode);
            });
        }
    }

    /**
     * Add a relation between a variable appearing on the left-hand side of a method call.
     *  e.g., VarType var = call(...);
     *
     * @param varDependency list of variables having a dependency with a method call.
     */
    public void setCallNodesVarDependency(List<Pair<String, String>> varDependency) {
        if (varDependency != null) {
            varDependency.forEach(v -> {
                Optional<IdentifierNode> call = getNodeWithFeature(v.a, "call");
                Optional<IdentifierNode> var = getNodeWithFeature(v.b, "var");

                if (call.isPresent() && var.isPresent()) {
                    IdentifierNode callNode = call.get();
                    IdentifierNode varNode = var.get();
                    if (!edgeBetweenNodes(varNode, callNode)) {
                        graph.addEdge(varNode, callNode, new IdentifierRelationEdge("calls", varNode, callNode));
                    }
                }
            });
        }
    }

    /**
     * Add a relation between the scope of a method call and the method call.
     *   e.g., scope.call(...);
     *
     * @param scopes list of calls as pairs of call name and call scope.
     */
    public void setCallScopes(List<Pair<String, String>> scopes) {
        if (scopes != null) {
            scopes.stream().filter(Objects::nonNull).forEach(s -> {
                Optional<IdentifierNode> call = getNodeWithFeature(s.a, "call");
                Optional<IdentifierNode> scope = getNode(s.b);

                if (call.isPresent()) {
                    IdentifierNode callNode = call.get();
                    if (scope.isPresent()) {
                        IdentifierNode scopeNode = scope.get();
                        if (!edgeBetweenNodes(scopeNode, callNode)) {
                            graph.addEdge(scopeNode, callNode, new IdentifierRelationEdge("scope", scopeNode, callNode));
                        }
                    } else {
                        IdentifierNode scopeNode = new IdentifierNode(s.b, "id");
                        graph.addEdge(scopeNode, callNode, new IdentifierRelationEdge("scope", scopeNode, callNode));
                    }
                }
            });
        }
    }

    /**
     * @param args
     */
    public void setCallArguments(List<Pair<String, Pair<String, String>>> args) {
        if (args != null) {
            args.forEach(arg -> {
                Optional<IdentifierNode> call = getNodeWithFeature(arg.a, "call");

                Optional<IdentifierNode> argN;
                if (arg.b.b.equals("call")) {
                    argN = getNodeWithFeature(arg.b.a, "call");
                } else {
                    argN = getNodeWithFeature(arg.b.a, "param");
                    if (argN.isEmpty()) {
                        argN = getNodeWithFeature(arg.b.a, "var");
                    }
                }

                IdentifierNode argNode = null;
                if (argN.isEmpty() && !arg.b.b.equals("call")) {
                    argNode = new IdentifierNode(arg.b.a, "id");
                    graph.addNode(argNode);
                } else if (argN.isPresent()) {
                    argNode = argN.get();
                }
                if (call.isPresent() && argNode != null) {
                    IdentifierNode callNode = call.get();
                    if (!edgeNamedBetweenNodes(callNode, argNode, "arg")) {
                        graph.addEdge(callNode, argNode, new IdentifierRelationEdge("arg", callNode, argNode));
                    }
                }
            });
        }
    }

    /**
     * @param assigns
     */
    public void setVarAssigns(List<Pair<String, Pair<String, String>>> assigns) {
        if (assigns != null) {
            assigns.forEach(assign -> {
                Optional<IdentifierNode> var = getNodeWithFeature(assign.a, "var");

                Optional<IdentifierNode> asgnN;
                if (assign.b.b.equals("call")) {
                    asgnN = getNodeWithFeature(assign.b.a, "call");
                } else {
                    asgnN = getNodeWithFeature(assign.b.a, "param");
                    if (asgnN.isEmpty()) {
                        asgnN = getNodeWithFeature(assign.b.a, "var");
                    }
                }

                IdentifierNode assignNode = null;
                if (asgnN.isEmpty() && !assign.b.b.equals("call")) {
                    assignNode = new IdentifierNode(assign.b.a, "id");
                    graph.addNode(assignNode);
                } else if (asgnN.isPresent()) {
                    assignNode = asgnN.get();
                }
                if (var.isPresent() && assignNode != null) {
                    IdentifierNode varNode = var.get();
                    if (!edgeBetweenNodes(varNode, assignNode)) {
                        graph.addEdge(varNode, assignNode, new IdentifierRelationEdge("relatedTo", varNode, assignNode));
                    }
                }
            });
        }
    }

    public void checkRootRelations() {
        IdentifierNode rootNode = getMethodNode();
        graph.nodes().stream()
                .filter(n -> !n.getFeature().equals("method"))
                .forEach(n -> {
                    if (!edgeBetweenNodes(rootNode, n)) {
                        graph.addEdge(rootNode, n, new IdentifierRelationEdge("contains", rootNode, n));
                    }
                });
    }

    /**
     * Get the root node of the graph (e.g., the method node).
     *
     * @return the method node, if exists, otherwise null.
     */
    public IdentifierNode getMethodNode() {
        Optional<IdentifierNode> method = graph.nodes().stream()
                .filter(n -> n.getFeature().equals("method"))
                .findAny();
        return method.orElse(null);
    }

    /**
     * Check whether a node already exists in the graph.
     *
     * @param label     label of the node
     * @param feature   feature of the node
     * @return true if the node exists in the graph, false otherwise.
     */
    boolean nodeAndFeatureInGraph(String label, String feature) {
        return graph.nodes().stream()
                .anyMatch(n -> n.getLabel().equals(label) && n.getFeature().equals(feature));
    }

    /**
     * Get a specific node in the graph.
     *
     * @param label     label of the node
     * @param feature   feature of the node
     * @return the corresponding node in the graph.
     */
    Optional<IdentifierNode> getNodeWithFeature(String label, String feature) {
        return graph.nodes().stream()
                .filter(n -> n.getLabel().equals(label) && n.getFeature().equals(feature))
                .findAny();
    }

    /**
     * Get a node in the graph.
     *
     * @param label label of the node to search
     * @return the node if it exists.
     */
    Optional<IdentifierNode> getNode(String label) {
        return graph.nodes().stream()
                .filter(n -> n.getLabel().equals(label) && !n.getFeature().equals("method"))
                .findAny();
    }

    /**
     * Check whether there exists an edge between two nodes.
     *
     * @param sourceNode    the source node of the edge.
     * @param targetNode    the target node of the edge.
     * @return true if an edge exists, otherwise false.
     */
    boolean edgeBetweenNodes(IdentifierNode sourceNode, IdentifierNode targetNode) {
        return !graph.edgesConnecting(sourceNode, targetNode).isEmpty();
    }

    /**
     * @param sourceNode
     * @param targetNode
     * @param relationName
     * @return
     */
    boolean edgeNamedBetweenNodes(IdentifierNode sourceNode, IdentifierNode targetNode, String relationName) {
        return graph.edgesConnecting(sourceNode, targetNode).stream().anyMatch(r -> r.getValue().equals(relationName));
    }

    /**
     * @param node
     * @return
     */
    boolean hasIncidentEdge(IdentifierNode node) {
        return !graph.incidentEdges(node).isEmpty();
    }

    public MutableNetwork<IdentifierNode, IdentifierRelationEdge> getGraph() {
        return graph;
    }

    public String printGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("graph {\n");
        graph.nodes().forEach(n -> sb.append(String.format("\tnode: %s\n", n)));
        graph.edges().forEach(e -> sb.append(String.format("\n\tedge: %s", e)));
        sb.append("\n}\n");
        return sb.toString();
    }

    public String toString() { return printGraph(); }
}
