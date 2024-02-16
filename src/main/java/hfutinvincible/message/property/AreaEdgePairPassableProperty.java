package hfutinvincible.message.property;

import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import hfutinvincible.debug.TestLogger;
import hfutinvincible.tool.edge.HFUTEdge;
import adf.core.component.communication.util.BitStreamReader;
import org.apache.log4j.Logger;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录边对可通行情况
 */
public class AreaEdgePairPassableProperty{

    //private static final Logger logger = TestLogger.getLogger("HFUTTool/HFUTMessageTool/AreaEdgePairPassableProperty");

    /**
     * 当前 Area
     */
    private Area area;

    /**
     * 各边对的是否互相可达. <br>
     * {@code edgePairsPassability[i] == true} 说明 {@code edgePairs[i]} 中两边互相可达.
     * {@code edgePairs}  计算方法如下：
     * <pre>{@literal
     * List<Edge> edges = area.getEdges();
     * List<Pair<Edge, Edge>> edgePairs = new ArrayList();
     * for (int i = 0; i < numEdges; i ++) {
     *     Edge ei = edges.get(i);
     *     if (!ei.isPassable()) continue;
     *     for (int j = i + 1; j < numEdges; j ++) {
     *         Edge ej = edges.get(j);
     *         if (!ei.isPassable()) continue;
     *         edgePairs.add(new Pair<>(ei, ej));
     *     }
     * }
     * }<\pre>
     */
    private List<Boolean> edgePairsPassability;

    private List<Pair<HFUTEdge, HFUTEdge>> passableEdgePairs;
    private List<Pair<HFUTEdge, HFUTEdge>> impassableEdgePairs;

    public AreaEdgePairPassableProperty(Area area, List<Boolean> edgePairsPassability) {
        this.area = area;
        this.edgePairsPassability = edgePairsPassability;
    }

    public AreaEdgePairPassableProperty(Area area, List<Boolean> edgePairsPassability,
                                        List<Pair<HFUTEdge, HFUTEdge>> passableEdgePairs,
                                        List<Pair<HFUTEdge, HFUTEdge>> impassableEdgePairs) {
        this.area = area;
        this.edgePairsPassability = edgePairsPassability;
        this.passableEdgePairs = passableEdgePairs;
        this.impassableEdgePairs = impassableEdgePairs;
    }

    public Area getArea() {
        return area;
    }

    public List<Boolean> getEdgePairsPassability() {
        return edgePairsPassability;
    }

    public List<Pair<HFUTEdge, HFUTEdge>> getPassableEdgePairs() {
        return passableEdgePairs;
    }

    public List<Pair<HFUTEdge, HFUTEdge>> getImpassableEdgePairs() {
        return impassableEdgePairs;
    }

    @Override
    public String toString() {
        return "[ area=" + getArea().getID() + "," + "edgePairsPassability" + getEdgePairsPassability() + " ]";
    }
}
