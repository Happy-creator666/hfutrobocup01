package hfutinvincible.tool.misc;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
//import hfutinvincible.tool.jigsaw.Jigsaw;
//import hfutinvincible.tool.jigsaw.JigsawTool;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.Objects;

/**
 * 对路网系统，使用曼哈顿距离估计两点间的通行代价是更合理的 (欧式距离通常会低估). <br>
 *
 * 对相同的两点，坐标系不同，其曼哈顿距离很可能不同. 如果两点间连线与坐标轴平行，
 * 则曼哈顿距离就等于两点间欧式距离; 如果两点间连线与坐标轴成一等腰三角形 (45°)，
 * 则曼哈顿距离等于欧式距离的 √2 倍. 曼哈顿距离只与笛卡尔坐标系的角度有关，与原点
 * 位置无关. <br>
 *
 * 那么对一个路网系统，坐标系的角度怎么选最合适呢？ 首先对于横平竖直的路网，横平
 * 竖直地建立坐标系显然是最好的. 但对于整体倾斜的路网，根据倾斜的角度，斜着建立
 * 坐标系更好. 路网整体倾斜角度的估计在 {@code calcCoordAngle} 中实现. <br>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Taxicab_geometry">Taxicab Geometry</a>
 *
 * @author stardust
 */
@HfutTool
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public class MeasureLengthTool implements LifeCycle {

    @Autowired
    WorldInfo worldInfo;

//  @Autowired private JigsawTool jigsawTool;

    private int baseAngle;

    public MeasureLengthTool() {
    }

    @Override
    public void precompute(PrecomputeData precomputeData) {
//            this.jigsawTool.precompute(precomputeData);
//            this.calcCoordAngle();
        // 写入预计算数据
        precomputeData.setInteger(MeasureLengthTool.class.getCanonicalName() + ".baseangle", this.baseAngle);
    }

    @Override
    public void resume(PrecomputeData precomputeData) {

//            this.jigsawTool.resume(precomputeData);
        // 读取预计算数据
        this.baseAngle = precomputeData.getInteger(MeasureLengthTool.class.getCanonicalName() + ".baseangle");
    }

    @Override
    public void preparate() {
//            this.jigsawTool.preparate();
//            this.calcCoordAngle();
    }

    /**
     * 计算两点间曼哈顿距离
     */
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

//    public double getManhattanLength(Jigsaw<? extends Area> from, Jigsaw<? extends Area> to) {
//        return this.getManhattanLength(from.getCenterArea(), to.getCenterArea());
//    }

    /**
     * 计算坐标系倾角，结果存入 {@code this.baseAngle}
     */
//    private void calcCoordAngle() {
//        // 将 180° 划分为长为 PATH_COORD_ANGLE_DELTA 的若干个区间
//        int[] counts = new int[180 / OptionsTool.PATH_COORD_ANGLE_DELTA];
//        // 对每条 Avenue，计算其倾斜角落在上面的哪个区间，增加相应计数
//        for (Avenue avenue : jigsawTool.getAvenues()) {
//            Area left = avenue.getLeftCross().getAdjoinedAvenues().get(avenue);
//            Area right = avenue.getRightCross().getAdjoinedAvenues().get(avenue);
//            double sx = left.getX();
//            double sy = left.getY();
//            double ex = right.getX();
//            double ey = right.getY();
//            double mx = avenue.getCenterPoint().getX();
//            double my = avenue.getCenterPoint().getY();
//            double arc1 = Math.atan2(sy - my, sx - mx);
//            double arc2 = Math.atan2(my - ey, mx - ex);
//            double diff = Math.abs(arc1 - arc2);
//            double tmp = Math.abs(arc1 + arc2);
//            double arc = (arc1 + arc2) / 2;
//            if (tmp < diff) {
//                diff = tmp;
//                arc += Math.PI;
//            }
//            // 这条 Avnue 过于扭曲，不参与整体倾斜角计算
//            if (diff > OptionsTool.PATH_COORD_IGNORE_THRESHOLD)
//                continue;
//            if (arc < 0)
//                arc += Math.PI;
//            if (arc >= Math.PI)
//                arc -= Math.PI;
//            int angle = (int) (arc * 180 / Math.PI);
//            counts[angle / OptionsTool.PATH_COORD_ANGLE_DELTA] ++;
//        }
//        // 使用最小方差估计法总体均值
//        double minVariance = Double.MAX_VALUE;
//        for (int a = OptionsTool.PATH_COORD_ANGLE_DELTA / 2; a <= 90; a += OptionsTool.PATH_COORD_ANGLE_DELTA) {
//            double variance = 0;
//            for (int i = 0; i < counts.length; i++) {
//                double diff = Math.abs(a - i * OptionsTool.PATH_COORD_ANGLE_DELTA);
//                while (diff > 45)
//                    diff -= 90;
//                variance += diff * diff * counts[i];
//            }
//            if (variance < minVariance) {
//                minVariance = variance;
//                this.baseAngle = a;
//            }
//        }
//    }
}