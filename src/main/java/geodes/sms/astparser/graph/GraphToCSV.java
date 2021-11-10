package geodes.sms.astparser.graph;

import com.google.common.graph.MutableNetwork;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

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

    private final List<CSVPrinter> csvPrinters;

    public GraphToCSV(String dir) {
        basePath = dir;
        csvPrinters = Stream.of("edges.csv", "nodes.csv", "num-edge-list.csv", "num-node-list.csv")
                .map(p -> Paths.get(basePath, p))
                .map(p -> {
                    try {
                        BufferedWriter writer = Files.newBufferedWriter(p);
                        return new CSVPrinter(writer, CSVFormat.DEFAULT);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    return null;
                }).collect(Collectors.toList());
    }

    public void writeGraphToCSV() {
        assert graph != null;
        try {
            writeNumNodeRecord();
            writeNumEdgeRecord();
            writeNodeRecords();
            writeEdgeRecords();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void writeNumNodeRecord() throws IOException {
        CSVPrinter printer = csvPrinters.get(3);
        printer.printRecord(String.valueOf(graph.nodes().size()));
        printer.flush();
    }

    private void writeNumEdgeRecord() throws IOException {
        CSVPrinter printer = csvPrinters.get(2);
        printer.printRecord(String.valueOf(graph.edges().size()));
        printer.flush();
    }

    private void writeNodeRecords() throws IOException {
        CSVPrinter printer = csvPrinters.get(1);
        graph.nodes().forEach(n -> {
            try {
                printer.printRecord(
                    String.valueOf(n.getIdCount()),
                    String.valueOf(n.getLabel()),
                    String.valueOf(NodeTypeEnum.valueOf(n.getType()).getId())
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        printer.flush();
    }

    private void writeEdgeRecords() throws IOException {
        CSVPrinter printer = csvPrinters.get(0);
        graph.edges().forEach(e -> {
            try {
                printer.printRecord(
                    String.valueOf(EdgeTypeEnum.valueOf(e.getValue()).getId()),
                    String.valueOf(e.getSourceNode().getIdCount()),
                    String.valueOf(e.getTargetNode().getIdCount())
                );
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        });
        printer.flush();
    }

    public MutableNetwork<IdentifierNode, IdentifierRelationEdge> getGraph() {
        return graph;
    }

    public void setGraph(MutableNetwork<IdentifierNode, IdentifierRelationEdge> graph) {
        this.graph = graph;
    }
}
