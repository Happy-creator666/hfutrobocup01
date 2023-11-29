//package hfutinvincible.tool.human;
//
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.anno.AgentType;
//import hfutinvincible.anno.Autowired;
//import hfutinvincible.anno.HfutTool;
//import hfutinvincible.tool.LifeCycle;
//import hfutinvincible.tool.comm.MessageManageTool;
//import org.apache.log4j.Logger;
//import rescuecore2.standard.entities.AmbulanceTeam;
//import rescuecore2.standard.entities.FireBrigade;
//import rescuecore2.standard.entities.PoliceForce;
//import rescuecore2.worldmodel.EntityID;
//
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//
//@HfutTool
//@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
//public class HumanPositionTool implements LifeCycle {
//
//    @Autowired
//    Logger logger;
//
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
//
//    public final Map<EntityID, HumanPositionRecord> recordMap;
//    private final Map<EntityID, Set<EntityID>> areaId2humanOnIt;
//    private final Set<EntityID> posChangedCvIdsInSight;
//    // 记录我访问一个 area 的次数
//    private final Map<EntityID, Integer> lastVisitTimeMap;
//
//
//    private final Map<EntityID, Integer> stayCountMap;
//
////    // Tools
////    private MessageManageTool messageTool;
////    private SearchPathTool searchPathTool;
////    private StuckInfoTool stuckInfoTool;
//    public HumanPositionTool(){
//        this.recordMap = new HashMap<>();
//        this.posChangedCvIdsInSight = new HashSet<>();
//        this.areaId2humanOnIt = new HashMap<>();
//
//        this.stayCountMap = new HashMap<>();
//        this.lastVisitTimeMap = new HashMap<>();
//    }
//
//}
