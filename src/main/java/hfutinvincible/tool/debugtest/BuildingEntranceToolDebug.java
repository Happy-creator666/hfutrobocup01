package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.*;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;

import java.awt.Color;

import javax.swing.text.html.parser.Entity;

import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.BuildingEntranceTool;

@HfutTool
@Disabled
@AgentType({ FireBrigade.class, AmbulanceTeam.class, PoliceForce.class })
public class BuildingEntranceToolDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    Logger logger;
    @Autowired
    BuildingEntranceTool buildingEntranceTool;

    public BuildingEntranceToolDebug() {

    }

    @After("BuildingEntranceTool")
    public void resume(PrecomputeData precomputeData) {
        logger.info("BuildingEntranceTool");
        for (StandardEntity standardEntity : worldInfo.getAllEntities()) {
            if (standardEntity instanceof Area area) {
                EntityID areaId = standardEntity.getID();
                double BuildingEntranceProximity = buildingEntranceTool.getBuildingEntranceProximityOf(areaId);
                double RefugeEntranceProximity = buildingEntranceTool.getRefugeEntranceProximityOf(areaId);
                Debugger.getPainterOfAgent(agentInfo)
                        .addPaintables("BuildingEntrance", false,
                                Paintable.text(((Area) standardEntity).getX() / 1.0,
                                        ((Area) standardEntity).getY() / 1.0,
                                        String.valueOf(BuildingEntranceProximity).substring(0, 3) + "," +
                                                String.valueOf(RefugeEntranceProximity).substring(0, 3),
                                        Color.BLUE));
            }
        }
    }

}
