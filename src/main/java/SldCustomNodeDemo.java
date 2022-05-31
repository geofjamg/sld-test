import com.powsybl.iidm.network.*;
import com.powsybl.sld.SingleLineDiagram;
import com.powsybl.sld.builders.NetworkGraphBuilder;
import com.powsybl.sld.layout.Layout;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.library.ComponentLibrary;
import com.powsybl.sld.library.ConvergenceComponentLibrary;
import com.powsybl.sld.library.ResourcesComponentLibrary;
import com.powsybl.sld.model.graphs.NodeFactory;
import com.powsybl.sld.model.graphs.VoltageLevelGraph;
import com.powsybl.sld.model.nodes.FeederInjectionNode;
import com.powsybl.sld.model.nodes.FeederNode;
import com.powsybl.sld.model.nodes.Node;
import com.powsybl.sld.svg.*;
import com.powsybl.sld.util.TopologicalStyleProvider;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class SldCustomNodeDemo {

    private SldCustomNodeDemo() {
    }

    /**
     * Very simple node/breaker network
     */
    private static Network createNetwork() {
        var network = Network.create("test", "");
        var s = network.newSubstation()
                .setId("S")
                .add();
        var vl = s.newVoltageLevel()
                .setId("VL")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl.getNodeBreakerView().newBusbarSection()
                .setId("BBS")
                .setNode(0)
                .add();
        var g = vl.newGenerator()
                .setId("G")
                .setNode(2)
                .setMinP(0)
                .setMaxP(5)
                .setTargetP(3)
                .setVoltageRegulatorOn(true)
                .setTargetV(18)
                .setEnergySource(EnergySource.WIND)
                .add();
        g.getTerminal().setP(-3).setQ(-2);
        vl.getNodeBreakerView().newDisconnector()
                .setId("D")
                .setNode1(0)
                .setNode2(1)
                .add();
        vl.getNodeBreakerView().newBreaker()
                .setId("BR")
                .setNode1(1)
                .setNode2(2)
                .add();
        return network;
    }

    private static class CustomNodeBreakerGraphBuilder extends NetworkGraphBuilder.NodeBreakerGraphBuilder {

        CustomNodeBreakerGraphBuilder(VoltageLevelGraph graph, Map<Integer, Node> nodesByNumber) {
            super(graph, nodesByNumber);
        }

        @Override
        public void visitGenerator(Generator generator) {
            // in case of a generator with wind as energy source, replace default generic generator node
            // by a custom one with WIND_TURBINE type.
            if (generator.getEnergySource() == EnergySource.WIND) {
                var node = new FeederInjectionNode(generator.getId(), generator.getOptionalName().orElse(null), "WIND_TURBINE");
                graph.addNode(node);
                addFeeder(node, generator.getTerminal());
            } else {
                super.visitGenerator(generator);
            }
        }
    }

    private static class CustomNetworkGraphBuilder extends NetworkGraphBuilder {

        public CustomNetworkGraphBuilder(Network network) {
            super(network);
        }

        @Override
        protected NodeBreakerGraphBuilder createNodeBreakerGraphBuilder(VoltageLevelGraph graph, Map<Integer, Node> nodesByNumber) {
            return new CustomNodeBreakerGraphBuilder(graph, nodesByNumber);
        }
    }

    /**
     * Custom SVG component library that extends ConvergenceLibrary by adding a wind turbine symbol.
     */
    private static class CustomLibrary extends ResourcesComponentLibrary {

        public CustomLibrary() {
            super("Custom", "/CustomLibrary", "/ConvergenceLibrary");
        }
    }

    public static void main(String[] args) throws IOException {
        // create network
        var network = createNetwork();

        // build SLD graph
        var graph = new CustomNetworkGraphBuilder(network)
                .buildVoltageLevelGraph("VL");

        // run layout
        var parameters = new LayoutParameters();
        var layout = new SmartVoltageLevelLayoutFactory(network)
                .create(graph);
        layout.run(parameters);

        // render SVG
        var componentLibrary = new CustomLibrary();
        var svgWriter = new DefaultSVGWriter(componentLibrary, parameters);
        var labelProvider = new DefaultDiagramLabelProvider(network, componentLibrary, parameters);
        var styleProvider = new TopologicalStyleProvider(network);
        try (Writer writer = Files.newBufferedWriter(Paths.get("/Users/geo/sld.svg"), StandardCharsets.UTF_8)) {
            svgWriter.write("", graph, labelProvider, styleProvider, writer);
        }
    }
}
