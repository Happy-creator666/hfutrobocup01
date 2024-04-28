package hfutinvincible.tool.misc;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.Objects;

@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class MeasureLengthTool implements LifeCycle {

//    private JigsawTool jigsawTool;

    private int baseAngle;
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    DevelopData developData;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    Logger logger;

    @Override
    public void precompute(PrecomputeData precomputeData) {
        precomputeData.setInteger(MeasureLengthTool.class.getCanonicalName()+".baseangle",this.baseAngle);
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        this.baseAngle=precomputeData.getInteger(MeasureLengthTool.class.getCanonicalName()+".baseangle");
    }

    @Override
    public void preparate() {
        LifeCycle.super.preparate();
    }

   /**计算两点之间的曼哈顿距离*/
    public double getManhattanLength(int fromX, int fromY, int toX, int toY) {
        int dX = toX - fromX;
        int dY = toY - fromY;
        double length = Math.hypot(dX, dY);
        double theta = Math.atan2(dY, dX); // 计算与水平方向倾角
        theta -= baseAngle * Math.PI / 180.0; // 减去坐标轴倾角
        return Math.abs(length * Math.sin(theta)) + Math.abs(length * Math.cos(theta));
    }

    public double getManhattanLength(Pair<Integer, Integer> from, Pair<Integer, Integer> to) {
        return this.getManhattanLength(from.first(), from.second(), to.first(), to.second());
    }

    public double getManhattanLength(Point2D from, Point2D to) {
        return this.getManhattanLength((int) from.getX(), (int) from.getY(),
                (int) to.getX(), (int) to.getY());
    }

    public double getManhattanLength(Area from, Area to) {
        return this.getManhattanLength(from.getX(), from.getY(), to.getX(), to.getY());
    }

    public double getManhattanLength(EntityID from, EntityID to) {
        return this.getManhattanLength(Objects.requireNonNull(worldInfo.getLocation(from)),
                Objects.requireNonNull(worldInfo.getLocation(to)));
    }


}
