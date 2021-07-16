package geodes.sms.astparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.github.javaparser.utils.Pair;

import com.google.common.graph.MutableNetwork;

import geodes.sms.astparser.graph.GraphBuilder;
import geodes.sms.astparser.graph.IdentifierNode;
import geodes.sms.astparser.graph.IdentifierRelationEdge;

import java.util.*;
import java.util.stream.Collectors;


public class FileASTParser {

    private final CompilationUnit cu;

    private final String filePath;

    private Set<String> imports;

    private List<MutableNetwork<IdentifierNode, IdentifierRelationEdge>> graphs;

    FileASTParser(CompilationUnit cu, String filePath) {
        this.cu = cu;
        this.filePath = filePath;
        initializeImports();

        List<Method> methods = cu.findAll(MethodDeclaration.class).stream()
                .map(Method::new)
                .collect(Collectors.toList());
    }

    private void initializeImports() {
        imports = cu.findAll(ImportDeclaration.class).stream()
                .map(i -> i.getName().toString())
                .collect(Collectors.toSet());
    }

    class Method {
        private final MethodDeclaration methodData;

        private final String methodName;

        private final Identifier method;

        private List<Identifier> exceptions;

        private List<TypedIdentifier> parameters;

        private List<TypedIdentifier> variables;

        private List<Identifier> calls;

        private GraphBuilder graphBuilder;

        Method(MethodDeclaration m) {
            methodData = m;
            methodName = m.getName().toString();

            method = new Identifier(methodName);
            method.setNode(new IdentifierNode(methodName, "method"));

            graphBuilder = new GraphBuilder(method.getNode());
            graphBuilder.setExceptionNodes(retrieveExceptions());
            graphBuilder.setParameterNodes(retrieveParameters());
            graphBuilder.setVariableNodes(retrieveVariables());
            graphBuilder.setVariableCasts(retrieveCasts());
            graphBuilder.setCallNodes(retrieveCalls());
            graphBuilder.setCallNodesVarDependency(retrieveCallVarDependency());
            graphBuilder.setCallScopes(retrieveCallScopes());
            graphBuilder.setCallArguments(retrieveCallArgs());
            graphBuilder.setVarAssigns(retrieveVarAssigns());

            System.out.println(this);
        }

        /**
         * @return
         */
        public List<String> retrieveExceptions() {
            return methodData.getThrownExceptions().stream()
                    .map(Node::toString)
                    .collect(Collectors.toList());
        }

        /**
         * @return
         */
        public List<Pair<String, String>> retrieveParameters() {
            return methodData.getParameters().stream()
                    .map(p -> {
                        Optional<SimpleName> paramType = p.getType().findFirst(SimpleName.class);
                        return paramType.map(simpleName -> new Pair<>(
                                p.getNameAsString(),
                                simpleName.toString())).orElseGet(() -> new Pair<>(p.getNameAsString(), null));
                    }).collect(Collectors.toList());
        }

        /**
         * Retrieve all variables and their corresponding types declared in the body of the method.
         *
         * @return list of all variables (name and type) appearing in the method body as pairs of strings.
         */
        public List<Pair<String, String>> retrieveVariables() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(VariableDeclarator.class).stream()
                        .map(v -> {
                            Optional<SimpleName> varType = v.getType().findFirst(SimpleName.class);
                            return varType.map(simpleName -> new Pair<>(
                                    v.getNameAsString(),
                                    simpleName.toString())).orElseGet(() -> new Pair<>(v.getNameAsString(), null));
                        }).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<Pair<String, String>> retrieveCasts() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(CastExpr.class).stream()
                    .map(c -> {
                        Optional<SimpleName> castType = c.getType().findFirst(SimpleName.class);
                        return castType.map(simpleName -> new Pair<>(
                                c.getExpression().toString(),
                                simpleName.toString())).orElse(null);
                    }).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<String> retrieveCalls() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(MethodCallExpr.class).stream()
                        .map(MethodCallExpr::getNameAsString)
                        .distinct()
                        .collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<Pair<String, String>> retrieveCallVarDependency() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(VariableDeclarator.class).stream()
                        .map(v -> {
                            Optional<MethodCallExpr> call = v.findAll(MethodCallExpr.class).stream().findFirst();
                            return call.map(callNode -> new Pair<>(
                                    callNode.getNameAsString(),
                                    v.getNameAsString())).orElse(null);
                        }).filter(Objects::nonNull).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<Pair<String, String>> retrieveCallScopes() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(MethodCallExpr.class).stream()
                        .map(m -> {
                            Optional<Expression> scope = m.getScope();
                            if (scope.isPresent()) {
                                Expression s = scope.get();
                                // remove element between parentheses to avoid to match previous
                                //   method call arguments as part of the scope of the current method call
                                List<String> sElement = Arrays.asList(
                                        s.toString().replaceAll("\\(.*?\\)", "").split("\\."));
                                return new Pair<>(m.getNameAsString(), sElement.get(sElement.size() - 1));
                            }
                            return null;
                        }).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<Pair<String, Pair<String, String>>> retrieveCallArgs() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(MethodCallExpr.class).stream()
                        .flatMap(m -> m.getArguments().stream()
                                .map(a -> {
                                    if (a instanceof MethodCallExpr) {
                                        Pair<String, String> arg = new Pair<>(((MethodCallExpr) a).getNameAsString(), "call");
                                        return new Pair<>(m.getNameAsString(), arg);
                                    } else if (a instanceof NameExpr) {
                                        Pair<String, String> arg = new Pair<>(a.toString(), "var");
                                        return new Pair<>(m.getNameAsString(), arg);
                                    } return null;
                                }).filter(Objects::nonNull)
                        ).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        public List<Pair<String, Pair<String, String>>> retrieveVarAssigns() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(AssignExpr.class).stream().map(e -> {
                    if (e.getTarget() instanceof NameExpr) {
                        if (e.getValue() instanceof MethodCallExpr) {
                            Pair<String, String> value = new Pair<>(((MethodCallExpr) e.getValue()).getNameAsString(), "call");
                            return new Pair<>(e.getTarget().toString(), value);
                        } else if (e.getValue() instanceof NameExpr) {
                            Pair<String, String> value = new Pair<>(e.getValue().toString(), "var");
                            return new Pair<>(e.getTarget().toString(), value);
                        }
                    } return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
            } return null;
        }

        public String toString() {
            return String.format("%s\n%s\n%s\n", methodName, "=".repeat(methodName.length()), graphBuilder.toString());
        }
    }
}
