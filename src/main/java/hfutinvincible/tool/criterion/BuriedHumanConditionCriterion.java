package hfutinvincible.tool.criterion;

import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class BuriedHumanConditionCriterion implements LifeCycle,ICriterion<Area> {
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    BuriednessTool buriednessTool;
    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {
        // 每个区域 priority 初始值为 0
        int candidateNum = candidates.size();
        final DoubleMatrix priorityOfBuriedHuman = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix numOfBuriedHumansWorthRescuing = DoubleMatrix.zeros(candidateNum);

        Map<EntityID, Integer> candidateId2index = IntStream.range(0, candidateNum)
                .boxed()
                .collect(Collectors.toMap(i -> candidates.get(i).getID(), i -> i));

        buriednessTool.getBuriedHumanIds()
                .forEach(buriedHumanId -> {
                    EntityID posAreaId = humanPositionTool.getPositionAreaId(buriedHumanId);
                    if (posAreaId == null) return;
                    Integer index = candidateId2index.get(posAreaId);
                    if (index == null) return; // 不是候选 area
                    Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(buriedHumanId);
                    if (timeToDead == null) // 如果不知道 timeToDead
                        timeToDead = OptionsTool.TIME_TO_DEAD_UNKNOWN;
                    Integer buriedness = buriednessTool.getBuriedness(buriedHumanId);
                    if (buriedness == null)
                        buriedness = 0; //  如果不知道 buriedness，默认为 0

                    // 因为消防一次只能挖一个人，但如果一个区域上有多个人的话，可以不用移动就挖出好几个人来
                    // 因此消防的综合策略介于警察和救护之间，将所有人的优先级加起来作为总优先级
                    // TODO 是否合理
                    double delta = fun(timeToDead - buriedness);
                    priorityOfBuriedHuman.put(index, priorityOfBuriedHuman.get(index) + delta);

                    if (timeToDead - buriedness > OptionsTool.TIME_TO_DEAD_A)
                        numOfBuriedHumansWorthRescuing.put(index, numOfBuriedHumansWorthRescuing.get(index) + 1.0);
                });

        return Map.of(
                CriterionEnum.priorityOfBuriedHuman, priorityOfBuriedHuman,
                CriterionEnum.numOfBuriedHumansWorthRescuing, numOfBuriedHumansWorthRescuing
        );
    }
    // 一个乱取的评估函数，需要改
    private double fun(int timeToDead) {
        if (timeToDead < OptionsTool.TIME_TO_DEAD_A) // 马上就要死了，没有必要救
            return 0;
        else if (timeToDead < OptionsTool.TIME_TO_DEAD_B) // 上升部分
            return (1.0 / (OptionsTool.TIME_TO_DEAD_B - OptionsTool.TIME_TO_DEAD_A)) * (timeToDead - OptionsTool.TIME_TO_DEAD_A);
        else if (timeToDead < OptionsTool.TIME_TO_DEAD_C) // 下降部分
            return OptionsTool.TIME_TO_DEAD_D + (OptionsTool.TIME_TO_DEAD_C - timeToDead) * (1.0 - OptionsTool.TIME_TO_DEAD_D) / (OptionsTool.TIME_TO_DEAD_C - OptionsTool.TIME_TO_DEAD_B);
        else return OptionsTool.TIME_TO_DEAD_D; // 还能活很久的人所在区域，优先级为一个定值
    }
}
