package hfutinvincible.debug.parametermodification;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.*;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.mediator.AtMediator;
import hfutinvincible.tool.mediator.FbMediator;
import hfutinvincible.tool.mediator.PfMediator;
import hfutinvincible.tool.targetprovider.AtTargetProvider;
import hfutinvincible.tool.targetprovider.FbTargetProvider;
import hfutinvincible.tool.targetprovider.PfTargetProvider;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@HfutTool
@Disabled
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class TargetWithItsScores implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    @Autowired
    AtTargetProvider atTargetProvider;
    @Autowired
    FbTargetProvider fbTargetProvider;
    @Autowired
    PfTargetProvider pfTargetProvider;
    @Autowired
    AtMediator atMediator;
    @Autowired
    FbMediator fbMediator;
    @Autowired
    PfMediator pfMediator;
    public List<Tuple2<Area,Double>> AtSortedTargets;
    public List<Tuple2<Area,Double>> FbSortedTargets;
    public List<Tuple2<Area,Double>> PfSortedTargets;

    public TargetWithItsScores(){
        this.AtSortedTargets = new ArrayList<>();
        this.FbSortedTargets = new ArrayList<>();
        this.PfSortedTargets = new ArrayList<>();
    }


    @Override
    public void afterCalc(CalcContext context) {
        AtSortedTargets = atTargetProvider.getSortedCandidatesAndScores();
        FbSortedTargets = fbTargetProvider.getSortedCandidatesAndScores();
        PfSortedTargets = pfTargetProvider.getSortedCandidatesAndScores();
        StandardEntity myEntity = agentInfo.me();
        if(myEntity instanceof FireBrigade){
            // 将我的目标和别人进行竞争如果竞争不过则从我的目标中删除
            AtSortedTargets.removeIf(target -> !atMediator.isAllowed(target.e0));
        }
        else if (myEntity instanceof AmbulanceTeam){
            FbSortedTargets.removeIf(target -> !fbMediator.isAllowed(target.e0));
        }
        else {
            PfSortedTargets.removeIf(target -> !pfMediator.isAllowed(target.e0));
        }
    }
    public List<Tuple2<Area,Double>> getAtSortedTargets(){
        if(this.AtSortedTargets.isEmpty()){
            logger.error("TargetWithItsScores has no AtTargets");
            return Collections.emptyList();
        }
        return this.AtSortedTargets;
    }
    public List<Tuple2<Area,Double>> getFbSortedTargets(){
        if(this.AtSortedTargets.isEmpty()){
            logger.error("TargetWithItsScores has no FbTargets");
            return Collections.emptyList();
        }
        return this.FbSortedTargets;
    }
    public List<Tuple2<Area,Double>> getPfSortedTargets(){
        if(this.AtSortedTargets.isEmpty()){
            logger.error("TargetWithItsScores has no PfTargets");
            return Collections.emptyList();
        }
        return this.PfSortedTargets;
    }
}
