package hfutinvincible.tool.executor;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.SearchPathTool;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class AgentRestTool implements LifeCycle {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private SearchPathTool searchPathTool;
    @Autowired
    private HumanPositionTool humanPositionTool;

    public AgentRestTool() {

    }

    @Override
    public void calc(CalcContext context) {
        Human me = (Human) agentInfo.me();
        if (needRest(me)) {
            List<EntityID> path = null;
            double minPassCost = Double.MAX_VALUE;
            Area myPosArea = humanPositionTool.getMyPositionArea();
            if (myPosArea instanceof Refuge) {
                context.setResultAction(new ActionRest());
            } else {
                Collection<StandardEntity> refuges = this.worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE);
                for (StandardEntity e : refuges) {
                    if (!(e instanceof Refuge refuge))
                        continue;
                    Path p = searchPathTool.setFromAndTo(myPosArea, refuge)
                            .calc()
                            .getAreaPath();
                    if (p == null)
                        continue;
                    double c = searchPathTool.getPassCost();
                    if (c < minPassCost) {
                        minPassCost = c;
                        path = p.getIDList();
                    }
                }
                if (path != null)
                    context.setResultAction(new ActionMove(path));
            }
        }
    }

    private boolean needRest(Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if(hp == 0 || damage == 0) {
            return false;
        }
        int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        return damage >= 100 || (activeTime + this.agentInfo.getTime()) < OptionsTool.KERNEL_TIMESTEPS;
    }
}