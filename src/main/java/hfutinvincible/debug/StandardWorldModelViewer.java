package hfutinvincible.debug;

import rescuecore2.standard.view.*;
import rescuecore2.view.LayerViewComponent;

public class StandardWorldModelViewer extends LayerViewComponent {
    public static final String STANDARD_VIEWER_PREFIX = "viewer.standard";

    public StandardWorldModelViewer() {
        this.addDefaultLayers();
    }

    public String getViewerName() {
        return "Standard world model viewer";
    }

    public void addDefaultLayers() {
        this.addLayer(new BuildingLayer());
        this.addLayer(new RoadLayer());
        this.addLayer(new AreaNeighboursLayer());
        this.addLayer(new RoadBlockageLayer());
        this.addLayer(new AreaIconLayer());
        this.addLayer(new HumanLayer());
        this.addLayer(new CommandLayer());
        this.addLayer(new PositionHistoryLayer());
    }
}
