package geodes.sms.astparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.github.javaparser.utils.Pair;

import com.google.common.graph.MutableNetwork;

import geodes.sms.astparser.graph.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


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
            methodName = m.getName().toString();

            Identifier method = new Identifier(methodName);
            method.setNode(new IdentifierNode(0, methodName, NodeTypeEnum.ROOT.name()));

            graphBuilder = new GraphBuilder(methodName);
            graphBuilder.setExceptionNodes(retrieveExceptions());
            graphBuilder.setParameterNodes(retrieveParameters());
            graphBuilder.setVariableNodes(retrieveVariables());
            // graphBuilder.setVariableCasts(retrieveCasts());
            graphBuilder.setCallNodes(retrieveCalls());
            graphBuilder.setCallNodesVarDependency(retrieveCallVarDependency());
            graphBuilder.setCallScopes(retrieveCallScopes());
            graphBuilder.setCallArguments(retrieveCallArgs());
            graphBuilder.setVarAssigns(retrieveVarAssigns());
            graphBuilder.checkRootRelations();
        }

        /**
         * Get the method content (javadoc, declaration and body) on a single line.
         *
         * @return the method content as a string
         */
        String getCleanedMethodContent() {
            return methodData.toString().replaceAll("[^\\S ]+", " ");
        }

        /**
         * @return
         */
        List<String> retrieveExceptions() {
            return methodData.getThrownExceptions().stream()
                    .map(Node::toString)
                    .filter(this::isValidIdentifierName)
                    .collect(Collectors.toList());
        }

        /**
         * @return
         */
        List<Pair<String, String>> retrieveParameters() {
            return methodData.getParameters().stream()
                    .filter(p -> isValidIdentifierName(p.getNameAsString()))
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
        List<Pair<String, String>> retrieveVariables() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(VariableDeclarator.class).stream()
                        .filter(v -> isValidIdentifierName(v.getNameAsString()))
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
        List<Pair<String, String>> retrieveCasts() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(CastExpr.class).stream()
                    .map(c -> {
                        Optional<SimpleName> castType = c.getType().findFirst(SimpleName.class);
                        System.out.println(c);
                        return castType.map(simpleName -> new Pair<>(
                                c.getExpression().toString(),
                                simpleName.toString())).orElse(null);
                    }).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        List<String> retrieveCalls() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(MethodCallExpr.class).stream()
                        .map(MethodCallExpr::getNameAsString)
                        .distinct()
                        .filter(this::isValidIdentifierName)
                        .collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        List<Pair<String, String>> retrieveCallVarDependency() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(VariableDeclarator.class).stream()
                        .filter(v -> isValidIdentifierName(v.getNameAsString()))
                        .map(v -> {
                            Optional<MethodCallExpr> call = v.findAll(MethodCallExpr.class).stream().findFirst();
                            return call.filter(c -> isValidIdentifierName(c.getNameAsString()))
                                    .map(callNode -> new Pair<>(
                                        callNode.getNameAsString(),
                                        v.getNameAsString())).orElse(null);
                        }).filter(Objects::nonNull).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        List<Pair<String, String>> retrieveCallScopes() {
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
                                String parsedScope = sElement.get(sElement.size() - 1);
                                if (isValidIdentifierName(m.getNameAsString()) && isValidIdentifierName(parsedScope))
                                    return new Pair<>(m.getNameAsString(), parsedScope);
                            }
                            return null;
                        }).filter(Objects::nonNull).collect(Collectors.toList());
            } return null;
        }

        /**
         * @return
         */
        List<Pair<String, Pair<String, String>>> retrieveCallArgs() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(MethodCallExpr.class).stream()
                        .flatMap(m -> m.getArguments().stream()
                                .map(a -> {
                                    if (a instanceof MethodCallExpr && isValidIdentifierName(((MethodCallExpr) a).getNameAsString())) {
                                        Pair<String, String> arg = new Pair<>(((MethodCallExpr) a).getNameAsString(), "call");
                                        return new Pair<>(m.getNameAsString(), arg);
                                    } else if (a instanceof NameExpr && isValidIdentifierName(a.toString())) {
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
        List<Pair<String, Pair<String, String>>> retrieveVarAssigns() {
            Optional<BlockStmt> body = methodData.getBody();
            if (body.isPresent()) {
                BlockStmt bodyData = body.get();
                return bodyData.findAll(AssignExpr.class).stream().map(e -> {
                    if (e.getTarget() instanceof NameExpr) {
                        if (e.getValue() instanceof MethodCallExpr && isValidIdentifierName(((MethodCallExpr) e.getValue()).getNameAsString())) {
                            Pair<String, String> value = new Pair<>(((MethodCallExpr) e.getValue()).getNameAsString(), "call");
                            return new Pair<>(e.getTarget().toString(), value);
                        } else if (e.getValue() instanceof NameExpr && isValidIdentifierName(e.getValue().toString())) {
                            Pair<String, String> value = new Pair<>(e.getValue().toString(), "var");
                            return new Pair<>(e.getTarget().toString(), value);
                        }
                    } return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
            } return null;
        }

        boolean isValidIdentifierName(String name) {
            return Pattern.matches("^([a-zA-Z_$][a-zA-Z\\d_$]*)$", name);
        }

        public String toString() {
            return String.format("%s\n%s\n%s\n", methodName, "=".repeat(methodName.length()), graphBuilder.toString());
        }
    }
}
