package Triskel;

import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.core.functiongraph.graph.FGEdge;
import ghidra.app.plugin.core.functiongraph.graph.FunctionGraph;
import ghidra.app.plugin.core.functiongraph.graph.layout.AbstractFGLayout;
import ghidra.app.plugin.core.functiongraph.graph.layout.FGLayout;
import ghidra.app.plugin.core.functiongraph.graph.layout.FGLayoutProviderExtensionPoint;
import ghidra.app.plugin.core.functiongraph.graph.vertex.FGVertex;
import ghidra.framework.Application;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.graph.VisualGraph;
import ghidra.graph.viewer.layout.*;
import ghidra.graph.viewer.vertex.VisualGraphVertexShapeTransformer;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;
import resources.ResourceManager;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.*;

import javax.swing.Icon;

import jtriskel.*;

//@formatter:off
@PluginInfo(
	status = PluginStatus.STABLE,
	packageName = "Triskel",
	category = PluginCategoryNames.GRAPH,
	shortDescription = "SESE based graph layouts.",
	description = "SESE based graph layouts."
)

public class TriskelLayoutProvider extends FGLayoutProviderExtensionPoint {
    private static final String NAME = "Triskel Layout";

    @Override
    public String getLayoutName() {
        return NAME;
    }

    @Override
    public Icon getActionIcon() {
        return ResourceManager.loadImage("images/triskel.png");
    }

    @Override
    public int getPriorityLevel() {
        return 1000;
    }

    @Override
    public FGLayout getFGLayout(FunctionGraph graph, TaskMonitor monitor)
            throws CancelledException {
        return new TriskelGraphLayout(graph);
    }

    private class TriskelGraphLayout extends AbstractFGLayout {
        private HashMap<FGVertex, Long> node_map;
        private HashMap<FGEdge, Long> edge_map;
        private CFGLayout layout;

        private static void load_lib(){
            try {
                File libraryPath = Application.getOSFile("Triskel", System.mapLibraryName("jtriskel"));
                System.load(libraryPath.getAbsolutePath());
            } catch (Exception e){
                System.out.println("[triskel] Error loading jtriskel\n\n\n");
                System.err.println(e);
            }
        }

        protected TriskelGraphLayout(FunctionGraph graph) {
            super(graph, NAME);

            node_map = new HashMap<>();
            edge_map = new HashMap<>();

            load_lib();
        }

        @Override
        protected AbstractVisualGraphLayout<FGVertex, FGEdge> createClonedFGLayout(
                FunctionGraph newGraph) {
            return new TriskelGraphLayout(newGraph);
        }


        @Override
        protected Point2D getVertexLocation(FGVertex v, Column<FGVertex> col, Row<FGVertex> row, Rectangle bounds){
            long id = node_map.get(v);
            Point pt = layout.get_coords(id);
            return new Point2D.Double((double)pt.getX() + bounds.getWidth() / 2,(double) pt.getY() + bounds.getHeight() / 2);
        }


        @Override
        protected GridLocationMap<FGVertex, FGEdge> performInitialGridLayout(
                VisualGraph<FGVertex, FGEdge> g)
                throws CancelledException {
            GridLocationMap<FGVertex, FGEdge> gridLocations = new GridLocationMap<>();

            Collection<FGVertex> vertices_ = g.getVertices();
            Collection<FGEdge> edges = g.getEdges();

            LayoutBuilder builder = jtriskel.make_layout_builder();

            // Ugly
            List<FGVertex> vertices = new ArrayList<>(vertices_);
            vertices.sort(Comparator.comparing(v -> v.getVertexAddress().getOffset()));

            for (FGVertex v : vertices) {
                Rectangle bounds = v.getBounds();
                long id = builder.make_node((float)bounds.getWidth(), (float)bounds.getHeight());
                node_map.put(v, id);
            }

            for (FGEdge e : edges) {
                long from_id = node_map.get(e.getStart());
                long to_id = node_map.get(e.getEnd());

                long id = builder.make_edge(from_id, to_id);
                edge_map.put(e, id);
            }

            // TODO: I'd like a debug method for having users dump the graph
            // System.out.println(builder.graphviz());

            layout = builder.build();
            builder.delete();

            for (FGVertex v: vertices){
                long id = node_map.get(v);
                Point coords = layout.get_coords(id);
                gridLocations.set(v, (int)coords.getX(), (int)coords.getY());
            }

            return gridLocations;
        }

        @Override
        protected Map<FGEdge, List<Point2D>> positionEdgeArticulationsInLayoutSpace(
                VisualGraphVertexShapeTransformer<FGVertex> transformer,
                Map<FGVertex, Point2D> vertexLayoutLocations,
                Collection<FGEdge> edges, LayoutLocationMap<FGVertex, FGEdge> layoutLocations)
                 {
            Map<FGEdge, List<Point2D>> newEdgeArticulations = new HashMap<>();

            for (FGEdge e : edges) {
                List<Point2D> waypoints = new ArrayList<>();

                long id = edge_map.get(e);
                List<Point> ws = layout.get_waypoints(id);

                for (Point w: ws){
                    Point2D waypoint = new Point2D.Double( w.getX(),  w.getY());
                    waypoints.add(waypoint);
                }
                newEdgeArticulations.put(e, waypoints);
            }
            return newEdgeArticulations;
        }

        @Override
        protected boolean isCondensedLayout() {
            return false;
        }

    }

}