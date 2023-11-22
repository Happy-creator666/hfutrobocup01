package hfutinvincible.tool;

import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.HfutTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class AreaPassableTool implements LifeCycle {

    /**
     * 判断同一个 area 上的两点是否互相可达
     * @param commonAreaId 两个点所在的 area 的 EntityID
     * @param x1 第一个点的 x 坐标
     * @param y1 第一个点的 y 坐标
     * @param x2 第二个点的 x 坐标
     * @param y2 第二个点的 y 坐标
     * @return 1 - 可达 <br>
     *         0 - 不可达 <br>
     *         -1 - 不知道
     */
    public int isReachable(
            @Nonnull EntityID commonAreaId,
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        throw new UnsupportedOperationException();
    }
}
