package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nullable;
import java.awt.geom.Area;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtTargetProviderTest implements LifeCycle,TargetProvider<Area> {

    // 防止因为在两个分区间移动导致前一个周期选中的目标下一个周期都不会作为候选，
    // 记录前一个周期的目标，并添加到这个周期的候选中
    private Area prevTarget;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;

    @Autowired
    StaticPartitionTool staticPartitionTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;


    @Override
    public void afterCalc(CalcContext context) {
        if(context.getTarget()!=null){
            prevTarget = context.getTarget();
        }
    }

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
        Map<Area,Integer> TargetWithItsPriority = new HashMap<>();
        return TargetWithItsPriority.entrySet().stream()
                .sorted(Map.Entry.<Area,Integer>comparingByValue())
                .map(res -> Tuple2.of(res.getKey(),(double)res.getValue()))
                .collect(Collectors.toList());
    }
}
