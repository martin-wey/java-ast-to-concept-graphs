package geodes.sms.astparser.graph;

import com.google.common.graph.MutableNetwork;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphToCSV {
    private final String basePath;

    private MutableNetwork<IdentifierNode, IdentifierRelationEdge> graph;

    private List<BufferedWriter> writers;

    public GraphToCSV(String dir) {
        basePath = dir;
        writers = Stream.of("edges.csv", "node-features.csv", "num-edge-list.csv", "num-node-list.csv")
                .map(p -> Paths.get(basePath, p))
                .map(p -> {
                    try {
                        return Files.newBufferedWriter(p);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    return null;
                }).collect(Collectors.toList());
    }

    public MutableNetwork<IdentifierNode, IdentifierRelationEdge> getGraph() {
        return graph;
    }

    public void setGraph(MutableNetwork<IdentifierNode, IdentifierRelationEdge> graph) {
        this.graph = graph;
    }
}
