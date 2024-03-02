//package hfutinvincible.tool.executor;
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.anno.AgentType;
//import hfutinvincible.anno.Autowired;
//import hfutinvincible.anno.HfutTool;
//import hfutinvincible.message.BuildingBrokenMessage;
//import hfutinvincible.message.RefugeConditionMessage;
//import hfutinvincible.tool.LifeCycle;
//import hfutinvincible.tool.UnavailableTargetTool;
//import hfutinvincible.tool.building.BuildingBrokennessTool;
//import hfutinvincible.tool.building.RefugeConditionTool;
//import hfutinvincible.tool.comm.MessageManageTool;
//import hfutinvincible.tool.edge.AreaPassableTool;
//import hfutinvincible.tool.human.*;
//import hfutinvincible.tool.mediator.AtMediator;
//import hfutinvincible.tool.path.PathUtilTool;
//import hfutinvincible.tool.path.SearchPathTool;
//import hfutinvincible.tool.targetprovider.AtTargetProviderTest;
//import org.apache.log4j.Logger;
//import rescuecore2.standard.entities.AmbulanceTeam;
//
//@HfutTool
//@AgentType({AmbulanceTeam.class})
//public class AtExecutor implements LifeCycle {
//
//    @Autowired
//    Logger logger;
//    @Autowired
//    AgentInfo agentInfo;
//    @Autowired
//    WorldInfo worldInfo;
//    @Autowired
//    ScenarioInfo scenarioInfo;
//    @Autowired
//    ModuleManager moduleManager;
//    @Autowired
//    DevelopData developData;
//    //Tools
//    @Autowired
//    SearchPathTool searchPathTool;
//    @Autowired
//    AtTargetProviderTest targetProvider;
//    @Autowired
//    BuildingBrokennessTool buildingBrokennessTool;
//    @Autowired
//    RefugeConditionTool refugeConditionTool;
//    @Autowired
//    AgentTargetAreaTool agentTargetAreaTool;
//    @Autowired
//    BuriednessTool buriednessTool;
//    @Autowired
//    StuckInfoTool stuckInfoTool;
//    @Autowired
//    TimeToDeadTool timeToDeadTool;
//    @Autowired
//    HumanPositionTool humanPositionTool;
//    @Autowired
//    MessageManageTool messageTool;
//    @Autowired
//    AreaPassableTool areaPassableTool;
//    @Autowired
//    PathUtilTool pathUtilTool;
//    @Autowired
//    UnavailableTargetTool unavailableTargetTool;
//    @Autowired
//    AtMediator atMediator;
//    @Autowired
//    FbAtStuckEscapeTool fbAtStuckEscapeTool;
//
//
//
//
//
//}
