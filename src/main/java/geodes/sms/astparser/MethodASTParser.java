package geodes.sms.astparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithIdentifier;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.github.javaparser.utils.Pair;

import com.google.common.graph.MutableNetwork;

import geodes.sms.astparser.graph.*;

import java.util.*;


public class MethodASTParser {
    private final List<Pair<String, MutableNetwork<IdentifierNode, IdentifierRelationEdge>>> methodGraphs
            = new ArrayList<>();

    MethodASTParser(List<Pair<String, MethodDeclaration>> methods) {
        methods.forEach(p -> {
            Method method = new Method(p.b);
            methodGraphs.add(new Pair<>(p.a, method.graphBuilder.getGraph()));
        });
    }

    public List<Pair<String, MutableNetwork<IdentifierNode, IdentifierRelationEdge>>> getMethodGraphs() {
        return methodGraphs;
    }

    class Method {
        private final MethodDeclaration methodData;

        private final String methodName;

        private final GraphBuilder graphBuilder;

        Method(MethodDeclaration m) {
            methodData = m;
            methodName = m.getNameAsString();

            graphBuilder = new GraphBuilder(methodName, methodData.hashCode());
            initMethodParametersNodes();
            initMethodIdentifiersNodes();
            initVarTypeEdges();
            initCallsScopes();
            initVarDependency();
            initCallsArgs();
            initVarAssigns();
            graphBuilder.checkUnconnectedNodes();
        }

        void initMethodParametersNodes() {
            methodData.getParameters()
                    .forEach(p -> {
                        graphBuilder.addNode(p.getNameAsString().hashCode(), p.getNameAsString(), NodeTypeEnum.PARAM.name());
                        if (!graphBuilder.nodeExists(p.getType().hashCode())) {
                            graphBuilder.addNode(p.getTypeAsString().hashCode(), p.getTypeAsString(), NodeTypeEnum.IMPORT.name());
                        }
                        Optional<IdentifierNode> param = graphBuilder.getNode(p.getNameAsString().hashCode());
                        Optional<IdentifierNode> type = graphBuilder.getNode(p.getTypeAsString().hashCode());
                        param.ifPresent(node -> {
                            type.ifPresent(identifierNode -> graphBuilder.addEdge(EdgeTypeEnum.TYPE.name(), node, identifierNode));
                            Optional<IdentifierNode> method = graphBuilder.getNode(methodData.hashCode());
                            method.ifPresent(identifierNode -> graphBuilder.addEdge(EdgeTypeEnum.PARAMETER.name(), identifierNode, param.get()));
                        });
                    });
        }

        void initMethodIdentifiersNodes() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(Node.class).stream()
                        .filter(n -> n instanceof NodeWithIdentifier).distinct()
                        .filter(i -> !graphBuilder.nodeExists(((NodeWithIdentifier<?>) i).getId().hashCode()))
                        .forEach(i -> {
                            if (i.getParentNode().isPresent()) {
                                switch (i.getParentNode().get().getClass().getSimpleName()) {
                                    case "ClassOrInterfaceType":
                                        graphBuilder.addNode(i.toString().hashCode(), i.toString(), NodeTypeEnum.IMPORT.name());
                                        break;
                                    case "VariableDeclarator":
                                        graphBuilder.addNode(i.toString().hashCode(), i.toString(), NodeTypeEnum.VAR.name());
                                        Optional<IdentifierNode> method = graphBuilder.getNode(methodData.hashCode());
                                        Optional<IdentifierNode> var = graphBuilder.getNode(i.toString().hashCode());
                                        if (method.isPresent() && var.isPresent()) {
                                            graphBuilder.addEdge(EdgeTypeEnum.DEFINES.name(), method.get(), var.get());
                                        }
                                        break;
                                    case "MethodCallExpr":
                                        graphBuilder.addNode(i.toString().hashCode(), i.toString(), NodeTypeEnum.CALL.name());
                                        break;
                                    case "NameExpr":
                                        graphBuilder.addNode(i.toString().hashCode(), i.toString(), NodeTypeEnum.ID.name());
                                        break;
                                    default:
                                        break;
                                }
                            }
                        });
            }
        }

        void initVarTypeEdges() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(VariableDeclarator.class)
                        .forEach(n -> {
                            Optional<IdentifierNode> var = graphBuilder.getNode(n.getNameAsString().hashCode());
                            Optional<IdentifierNode> type = graphBuilder.getNode(n.getTypeAsString().hashCode());
                            if (var.isPresent() && type.isPresent()) {
                                graphBuilder.addEdge(EdgeTypeEnum.TYPE.name(), var.get(), type.get());
                            }
                        });
            }
        }

        void initCallsScopes() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(MethodCallExpr.class)
                        .forEach(n -> {
                            Optional<Expression> scope = n.getScope();
                            if (scope.isPresent()) {
                                Expression s = scope.get();
                                // remove element between parentheses to avoid to match previous
                                //   method call arguments in the scope of the current method call
                                List<String> sElement = Arrays.asList(
                                        s.toString().replaceAll("\\(.*?\\)", "").split("\\."));
                                String parsedScope = sElement.get(sElement.size() - 1);
                                Optional<IdentifierNode> call = graphBuilder.getNode(n.getNameAsString().hashCode());
                                Optional<IdentifierNode> scopeNode = graphBuilder.getNode(parsedScope.hashCode());
                                if (scopeNode.isPresent() && call.isPresent()) {
                                    graphBuilder.addEdge(EdgeTypeEnum.SCOPE.name(), call.get(), scopeNode.get());
                                }
                            }
                        });
            }
        }

        void initVarDependency() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(VariableDeclarator.class)
                        .forEach(n -> {
                            Optional<Expression> varInit = n.getInitializer();
                            varInit.ifPresent(expression -> expression.findAll(Node.class).stream()
                                    .filter(m -> m instanceof NodeWithIdentifier).distinct()
                                    .filter(m -> ((NodeWithIdentifier<?>) m).getId().hashCode() != n.getNameAsString().hashCode())
                                    .forEach(m -> {
                                        Optional<IdentifierNode> var = graphBuilder.getNode(n.getNameAsString().hashCode());
                                        Optional<IdentifierNode> dep = graphBuilder.getNode(((NodeWithIdentifier<?>) m).getId().hashCode());
                                        if (var.isPresent() && dep.isPresent()) {
                                            graphBuilder.addEdge(EdgeTypeEnum.DEPENDS_ON.name(), var.get(), dep.get());
                                        }
                                    }));

                        });
            }
        }

        void initCallsArgs() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(MethodCallExpr.class).forEach(n -> {
                           n.getArguments().stream()
                                   .filter(m -> m instanceof MethodCallExpr || m instanceof NameExpr)
                                   .forEach(m -> {
                                       Optional<IdentifierNode> call = graphBuilder.getNode(n.getNameAsString().hashCode());
                                       Optional<IdentifierNode> arg = graphBuilder.getNode(((NodeWithSimpleName<?>) m).getNameAsString().hashCode());
                                       if (call.isPresent() && arg.isPresent()) {
                                           graphBuilder.addEdge(EdgeTypeEnum.ARG.name(), call.get(), arg.get());
                                       }
                                   });
                        });
            }
        }

        void initVarAssigns() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                bodyData.findAll(AssignExpr.class).forEach(n -> {
                    if (n.getTarget() instanceof NameExpr && n.getValue() instanceof MethodCallExpr || n.getValue() instanceof NameExpr) {
                        Optional<IdentifierNode> var = graphBuilder.getNode(n.getTarget().toString().hashCode());
                        Optional<IdentifierNode> value = graphBuilder.getNode(((NodeWithSimpleName<?>) n.getValue()).getNameAsString().hashCode());
                        if (var.isPresent() && value.isPresent()) {
                            graphBuilder.addEdge(EdgeTypeEnum.DEPENDS_ON.name(), var.get(), value.get());
                        }
                    }
                });
            }
        }

        public String toString() {
            return String.format("%s\n%s\n%s\n", methodName, "=".repeat(methodName.length()), graphBuilder.toString());
        }
    }
}
