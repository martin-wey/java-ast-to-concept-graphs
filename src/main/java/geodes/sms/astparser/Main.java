package geodes.sms.astparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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

        String basePath = "./src/main/resources/data";

        logger.info("Loading symbol resolver...");
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
        StaticJavaParser.getConfiguration().setAttributeComments(false);

        try {
            Files.walk(Paths.get(basePath))
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        String filePath = f.toString();
                        if (filePath.endsWith(".java")) {
                            try {
                                logger.info(String.format("Loading %s compilation unit...", filePath));
                                CompilationUnit cu = StaticJavaParser.parse(Paths.get(filePath));
                                logger.info(String.format("Parsing file: %s", filePath));
                                FileASTParser fileParser = new FileASTParser(cu, filePath);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
