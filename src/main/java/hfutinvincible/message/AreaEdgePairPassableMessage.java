package hfutinvincible.message;

import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.message.property.AreaEdgePairPassableProperty;
import hfutinvincible.tool.edge.HFUTEdge;
import adf.core.component.communication.util.BitStreamReader;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;

import java.util.List;

/**
 * 携带有一个道路通行信息的 Message，道路的通行情况用道路所有边对是否可达表示. <br>
 * 比如对 Road [e1, e2, e3, e4] (e1..4 为四条边，其中 e1, e3 为可通行的)，
 * 则其通行信息可用 (e1, e3) 表示.
 */
@HfutMessage
public class AreaEdgePairPassableMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.AREA_EDGE_PAIR_PASSABLE_PROPERTY)
    AreaEdgePairPassableProperty areaEdgePairPassableProperty;

    public AreaEdgePairPassableMessage(boolean isRadio, AreaEdgePairPassableProperty areaEdgePairPassableProperty) {
        super(isRadio);
        this.areaEdgePairPassableProperty = areaEdgePairPassableProperty;
    }

    public AreaEdgePairPassableMessage(boolean isRadio, Area area, List<Boolean> edgePairsPassability) {
        super(isRadio);
        this.areaEdgePairPassableProperty = new AreaEdgePairPassableProperty(area, edgePairsPassability);
    }

    public AreaEdgePairPassableMessage(boolean isRadio, int rawSenderId, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderId, ttl, bsr);
    }

    public Area getArea() {
        return this.areaEdgePairPassableProperty.getArea();
    }

    public List<Boolean> getEdgePairsPassability() {
        return this.areaEdgePairPassableProperty.getEdgePairsPassability();
    }

    public List<Pair<HFUTEdge, HFUTEdge>> getPassableEdgePairs() {
        return areaEdgePairPassableProperty.getPassableEdgePairs();
    }

    public List<Pair<HFUTEdge, HFUTEdge>> getImpassableEdgePairs() {
        return areaEdgePairPassableProperty.getImpassableEdgePairs();
    }
}
