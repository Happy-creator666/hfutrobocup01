package hfutinvincible.tool.MyTest.MessageTest;


import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AreaEdgePairPassableMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.edge.HFUTEdge;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Disabled
@HfutTool
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class AreaEdgePairPassableMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;
    @Autowired
    PropertyManageTool propertyManageTool;

    @Override
    public void afterCalc(CalcContext context) {
        EntityID agentPosAreaId = agentInfo.getPositionArea().getID();
        Area area = (Area) worldInfo.getEntity(agentPosAreaId);
        List<Pair<HFUTEdge, HFUTEdge>> pairList = propertyManageTool.getAreaEdgePairsMap().get(area.getID());
        List<Boolean> edgePairsPassability = new ArrayList<>(pairList.size());

        pairList.forEach(pair -> {
            //随机生成边对是否可通行
            boolean nextBoolean = ThreadLocalRandom.current().nextBoolean();
            edgePairsPassability.add(nextBoolean);
        });
        /*
        for (int i = 0; i < pairList.size(); i++) {
            if(i % 2 == 1){
                edgePairsPassability.add(true);
            }else {
                edgePairsPassability.add(false);
            }
        }*/

        messageManageTool.addMessage(new AreaEdgePairPassableMessage(true, area, edgePairsPassability));
    }
}
