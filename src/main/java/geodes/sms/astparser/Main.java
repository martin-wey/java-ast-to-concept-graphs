package geodes.sms.astparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.gson.Gson;
import geodes.sms.astparser.graph.GraphToCSV;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        InputStream stream = Main.class.getClassLoader().
                getResourceAsStream("logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(stream);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        Logger logger = Logger.getLogger(Main.class.getName());
        logger.setLevel(Level.INFO);

        String basePath = args[0];
        String outputDir = args[1];
        String outputFileName = args[2];

        logger.info("Loading symbol resolver...");
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
        // StaticJavaParser.getConfiguration().setAttributeComments(false);

        GraphToCSV graphWriter = new GraphToCSV(outputDir);
        StringBuffer methodsBuffer = new StringBuffer();

        try {
            Path methodFp = Paths.get(outputDir).resolve(outputFileName);
            if (!Files.exists(methodFp)) {
                Files.createFile(methodFp);
            }

            Gson gson = new Gson();
            AtomicInteger globalCounter = new AtomicInteger(0);
            Files.walk(Paths.get(basePath))
                    .map(Path::toString)
                    .filter(f -> f.endsWith(".jsonl"))
                    .forEach(f -> {
                        logger.info(String.format("Parsing file: %s", f));
                        try {
                            List<String> lines = Files.readAllLines(Paths.get(f));
                            logger.info(String.format("Number of methods to parse: %s", lines.size()));
                            MethodASTParser parser = new MethodASTParser(lines.stream().map(line -> {
                                try {
                                    Map jsonl = gson.fromJson(line, Map.class);
                                    MethodDeclaration method =  StaticJavaParser.parseMethodDeclaration(
                                        jsonl.get("original_string").toString());
                                    methodsBuffer.append(line);
                                    methodsBuffer.append("\n");
                                    return method;
                                } catch (Exception ignored) { }
                                return null;
                            }).filter(Objects::nonNull).collect(Collectors.toList()));

                            AtomicInteger counter = new AtomicInteger(0);
                            logger.info("Exporting method graphs...");
                            parser.getGraphs().forEach(g -> {
                                graphWriter.setGraph(g);
                                graphWriter.writeGraphToCSV();
                                graphWriter.setGraph(null);
                                counter.getAndIncrement();
                            });

                            logger.info("Writing methods contents in: " + methodFp);
                            Files.write(
                                methodFp,
                                String.valueOf(methodsBuffer).getBytes(),
                                StandardOpenOption.APPEND
                            );
                            methodsBuffer.delete(0, methodsBuffer.length());

                            logger.info(String.format("Number of parsed methods: %s", counter));
                            globalCounter.set(globalCounter.get() + counter.get());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            logger.info(String.format("Total number of parsed methods: %s", globalCounter));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
