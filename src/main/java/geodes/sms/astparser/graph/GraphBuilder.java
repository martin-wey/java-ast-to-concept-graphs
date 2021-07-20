package geodes.sms.astparser.graph;

import com.github.javaparser.utils.Pair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import java.util.List;
import java.util.Objects;
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

    private int nodeCounter = 0;

    private int edgeCounter = 0;

    private final Logger logger;

    public GraphBuilder(String methodName) {
        logger = Logger.getLogger(GraphBuilder.class.getName());
        logger.fine("Building graph for method: " + methodName);

        graph = NetworkBuilder.directed()
                .allowsSelfLoops(true)
                .allowsParallelEdges(true)
                .build();
        // the root node is the method node
        graph.addNode(new IdentifierNode(nodeCounter, methodName, NodeTypeEnum.ROOT.name()));
        nodeCounter++;
    }

    /**
     * Add exceptions thrown by the method to the graph.
     *
     * @param exceptions list of exception identifiers.
     */
    public void setExceptionNodes(List<String> exceptions) {
        exceptions.forEach(e -> {
            IdentifierNode exceptionNode = createNode(e, NodeTypeEnum.IMPORT.name());
            // the method depends on the exception which is imported
            createEdge(RelationEnum.DEPENDS_ON.name(), getMethodNode(), exceptionNode);
        });
    }

    /**
     * Add method parameters to the graph.
     *
     * @param parameters list of parameter identifiers with their corresponding types.
     */
    public void setParameterNodes(List<Pair<String, String>> parameters) {
        parameters.forEach(p -> {
            IdentifierNode paramIdNode = createNode(p.a, NodeTypeEnum.PARAM.name());
            // relation: method --param->
            createEdge(RelationEnum.PARAMETER.name(), getMethodNode(), paramIdNode);
            if (p.b != null && !nodeAndFeatureInGraph(p.b, NodeTypeEnum.IMPORT.name())) {
                // the type is an imported abstract type -> feature = import
                IdentifierNode paramTypeNode = createNode(p.b, NodeTypeEnum.IMPORT.name());
                // relation: method --dependsOn->
                createEdge(RelationEnum.DEPENDS_ON.name(), getMethodNode(), paramTypeNode);
                // relation: parameter --type->
                createEdge(RelationEnum.TYPE.name(), paramIdNode, paramTypeNode);
            } else if (p.b != null && nodeAndFeatureInGraph(p.b, NodeTypeEnum.IMPORT.name())) {
                Optional<IdentifierNode> varTypeNode = getNodeWithFeature(p.b, NodeTypeEnum.IMPORT.name());
                varTypeNode.ifPresent(node -> createEdge(RelationEnum.TYPE.name(), paramIdNode, node));
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
                if (!nodeAndFeatureInGraph(v.a, NodeTypeEnum.VAR.name())) {
                    IdentifierNode varIdNode = createNode(v.a, NodeTypeEnum.VAR.name());
                    if (v.b != null && !nodeAndFeatureInGraph(v.b, NodeTypeEnum.IMPORT.name())) {
                        IdentifierNode varTypeNode = createNode(v.b, NodeTypeEnum.IMPORT.name());
                        createEdge(RelationEnum.DEPENDS_ON.name(), getMethodNode(), varTypeNode);
                        createEdge(RelationEnum.TYPE.name(), varIdNode, varTypeNode);
                    } else if (v.b != null && nodeAndFeatureInGraph(v.b, NodeTypeEnum.IMPORT.name())) {
                        Optional<IdentifierNode> varTypeNode = getNodeWithFeature(v.b, NodeTypeEnum.IMPORT.name());
                        varTypeNode.ifPresent(typeNode -> createEdge(RelationEnum.TYPE.name(), varIdNode, typeNode));
                    }
                    createEdge(RelationEnum.DEFINES.name(), getMethodNode(), varIdNode);
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
                Optional<IdentifierNode> var = getNodeWithFeature(c.a, NodeTypeEnum.VAR.name());
                IdentifierNode varNode;
                if (var.isPresent()) {
                    varNode = var.get();
                } else {
                    varNode = createNode(c.a, NodeTypeEnum.VAR.name());
                    createEdge(RelationEnum.DEFINES.name(), getMethodNode(), varNode);
                }

                Optional<IdentifierNode> cast = getNodeWithFeature(c.b, NodeTypeEnum.IMPORT.name());
                IdentifierNode castNode;
                if (cast.isPresent()) {
                    castNode = cast.get();
                } else {
                    castNode = createNode(c.b, NodeTypeEnum.IMPORT.name());
                    createEdge(RelationEnum.DEPENDS_ON.name(), getMethodNode(), castNode);
                }
                createEdge(RelationEnum.TYPE.name(), varNode, castNode);
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
                graph.addNode(new IdentifierNode(nodeCounter, c, NodeTypeEnum.CALL.name()));
                nodeCounter++;
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
                Optional<IdentifierNode> call = getNodeWithFeature(v.a, NodeTypeEnum.CALL.name());
                Optional<IdentifierNode> var = getNodeWithFeature(v.b, NodeTypeEnum.VAR.name());

                if (call.isPresent() && var.isPresent()) {
                    IdentifierNode callNode = call.get();
                    IdentifierNode varNode = var.get();
                    if (!edgeBetweenNodes(varNode, callNode)) {
                        createEdge(RelationEnum.CALLS.name(), varNode, callNode);
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
                Optional<IdentifierNode> call = getNodeWithFeature(s.a, NodeTypeEnum.CALL.name());
                Optional<IdentifierNode> scope = getNode(s.b);

                if (call.isPresent()) {
                    IdentifierNode callNode = call.get();
                    if (scope.isPresent()) {
                        IdentifierNode scopeNode = scope.get();
                        if (!edgeBetweenNodes(scopeNode, callNode)) {
                            createEdge(RelationEnum.SCOPE.name(), scopeNode, callNode);
                        }
                    } else {
                        IdentifierNode scopeNode = createNode(s.b, NodeTypeEnum.ID.name());
                        createEdge(RelationEnum.SCOPE.name(), scopeNode, callNode);
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
                Optional<IdentifierNode> call = getNodeWithFeature(arg.a, NodeTypeEnum.CALL.name());

                Optional<IdentifierNode> argN;
                if (arg.b.b.equals(NodeTypeEnum.CALL.name())) {
                    argN = getNodeWithFeature(arg.b.a, NodeTypeEnum.CALL.name());
                } else {
                    argN = getNodeWithFeature(arg.b.a, NodeTypeEnum.PARAM.name());
                    if (argN.isEmpty()) {
                        argN = getNodeWithFeature(arg.b.a, NodeTypeEnum.VAR.name());
                    }
                }

                IdentifierNode argNode = null;
                if (argN.isEmpty() && !arg.b.b.equals(NodeTypeEnum.CALL.name())) {
                    argNode = createNode(arg.b.a, NodeTypeEnum.ID.name());
                } else if (argN.isPresent()) {
                    argNode = argN.get();
                }
                if (call.isPresent() && argNode != null) {
                    IdentifierNode callNode = call.get();
                    if (!edgeNamedBetweenNodes(callNode, argNode, RelationEnum.ARG.name())) {
                        createEdge(RelationEnum.ARG.name(), callNode, argNode);
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
                Optional<IdentifierNode> var = getNodeWithFeature(assign.a, NodeTypeEnum.VAR.name());

                Optional<IdentifierNode> asgnN;
                if (assign.b.b.equals(NodeTypeEnum.CALL.name())) {
                    asgnN = getNodeWithFeature(assign.b.a, NodeTypeEnum.CALL.name());
                } else {
                    asgnN = getNodeWithFeature(assign.b.a, NodeTypeEnum.PARAM.name());
                    if (asgnN.isEmpty()) {
                        asgnN = getNodeWithFeature(assign.b.a, NodeTypeEnum.VAR.name());
                    }
                }

                IdentifierNode assignNode = null;
                if (asgnN.isEmpty() && !assign.b.b.equals(NodeTypeEnum.CALL.name())) {
                    assignNode = createNode(assign.b.a, NodeTypeEnum.ID.name());
                } else if (asgnN.isPresent()) {
                    assignNode = asgnN.get();
                }
                if (var.isPresent() && assignNode != null) {
                    IdentifierNode varNode = var.get();
                    if (!edgeBetweenNodes(varNode, assignNode)) {
                        createEdge(RelationEnum.RELATED_TO.name(), varNode, assignNode);
                    }
                }
            });
        }
    }

    public void checkRootRelations() {
        IdentifierNode rootNode = getMethodNode();
        graph.nodes().stream()
                .filter(n -> !n.getFeature().equals(NodeTypeEnum.ROOT.name()))
                .forEach(n -> {
                    if (!edgeBetweenNodes(rootNode, n)) {
                        createEdge(RelationEnum.CONTAINS.name(), rootNode, n);
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
                .filter(n -> n.getFeature().equals(NodeTypeEnum.ROOT.name()))
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
                .filter(n -> n.getLabel().equals(label) && !n.getFeature().equals(NodeTypeEnum.ROOT.name()))
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

    IdentifierNode createNode(String name, String feat) {
        IdentifierNode node = new IdentifierNode(nodeCounter, name, feat);
        graph.addNode(node);
        nodeCounter++;
        return node;
    }

    void createEdge(String relation, IdentifierNode source, IdentifierNode target) {
        graph.addEdge(source, target, new IdentifierRelationEdge(edgeCounter, relation, source, target));
        edgeCounter++;
    }

    public MutableNetwork<IdentifierNode, IdentifierRelationEdge> getGraph() {
        return graph;
    }

    public String printGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("graph {\n");
        graph.nodes().forEach(n -> sb.append(String.format("\tnode: %s (id: %s)\n", n, n.getId())));
        graph.edges().forEach(e -> sb.append(String.format("\n\tedge: %s (id: %s)", e, e.getId())));
        sb.append("\n}\n");
        return sb.toString();
    }

    public String toString() { return printGraph(); }
}
