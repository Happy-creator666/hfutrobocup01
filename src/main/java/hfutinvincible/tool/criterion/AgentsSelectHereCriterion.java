package hfutinvincible.tool.criterion;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.human.AgentTargetAreaTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * 返回 7 个指标列：
 * <ol>
 *     <li>选择此 area 为目标，且编号小于我的 AT 数</li>
 *     <li>选择此 area 为目标，且编号大于我的 AT 数</li>
 *     <li>选择此 area 为目标，且编号小于我的 FB 数</li>
 *     <li>选择此 area 为目标，且编号大于我的 FB 数</li>
 *     <li>选择此 area 为目标，且编号小于我的 PF 数</li>
 *     <li>选择此 area 为目标，且编号大于我的 PF 数</li>
 * </ol>
 */
@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class AgentsSelectHereCriterion implements LifeCycle, ICriterion<Area> {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private AgentTargetAreaTool agentTargetAreaTool;

    public AgentsSelectHereCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final int candidateNum = candidates.size();

        final DoubleMatrix atSelectHereWhoseIdLtMe = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix atSelectHereWhoseIdGtMe = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix pfSelectHereWhoseIdLtMe = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix pfSelectHereWhoseIdGtMe = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix fbSelectHereWhoseIdLtMe = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix fbSelectHereWhoseIdGtMe = DoubleMatrix.zeros(candidateNum);

        int meRawId = agentInfo.getID().getValue();

        for (int i = 0; i < candidateNum; i ++) {
            Area here = candidates.get(i);
            for (EntityID agentId : agentTargetAreaTool.getAgentIdsSelectedHere(here.getID())) {
                int rawAgentId = agentId.getValue();
                if (rawAgentId == meRawId) {
                    continue;
                }
                StandardEntity agentEntity = worldInfo.getEntity(agentId);
                if (agentEntity instanceof AmbulanceTeam) {
                    if (rawAgentId < meRawId)
                        atSelectHereWhoseIdLtMe.put(i, atSelectHereWhoseIdLtMe.get(i) + 1);
                    else atSelectHereWhoseIdGtMe.put(i, atSelectHereWhoseIdGtMe.get(i) + 1);
                } else if (agentEntity instanceof PoliceForce) {
                    if (rawAgentId < meRawId)
                        pfSelectHereWhoseIdLtMe.put(i, pfSelectHereWhoseIdLtMe.get(i) + 1);
                    else pfSelectHereWhoseIdGtMe.put(i, pfSelectHereWhoseIdGtMe.get(i) + 1);
                } else if (agentEntity instanceof FireBrigade) {
                    if (rawAgentId < meRawId)
                        fbSelectHereWhoseIdLtMe.put(i, fbSelectHereWhoseIdLtMe.get(i) + 1);
                    else fbSelectHereWhoseIdGtMe.put(i, fbSelectHereWhoseIdGtMe.get(i) + 1);
                }
            }
        }

        return Map.of(
                CriterionEnum.atSelectHereWhoseIdLtMe, atSelectHereWhoseIdLtMe,
                CriterionEnum.atSelectHereWhoseIdGtMe, atSelectHereWhoseIdGtMe,
                CriterionEnum.pfSelectHereWhoseIdLtMe, pfSelectHereWhoseIdLtMe,
                CriterionEnum.pfSelectHereWhoseIdGtMe, pfSelectHereWhoseIdGtMe,
                CriterionEnum.fbSelectHereWhoseIdLtMe, fbSelectHereWhoseIdLtMe,
                CriterionEnum.fbSelectHereWhoseIdGtMe, fbSelectHereWhoseIdGtMe
        );
    }
}