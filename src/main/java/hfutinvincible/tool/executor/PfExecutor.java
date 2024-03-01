package hfutinvincible.tool.executor;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.MiscLayer;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.area.ToJts;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.AgentTargetAreaTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.mediator.PfMediator;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.PfTargetProviderTest;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.buffer.BufferParameters;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.locationtech.jts.algorithm.Angle.angle;
import static org.locationtech.jts.algorithm.Angle.normalizePositive;

@HfutTool
@AgentType({PoliceForce.class})
public class PfExecutor implements LifeCycle {
    private static final GeometryFactory GF = new GeometryFactory();

    /** 前一周期的引导点坐标（可能为空） */
    private Coordinate prevGuideCoord;
    /** 上一个周期是否在救人，如果是，则不应使用重复清理策略 */
    private boolean isPrevSaveHuman;
    private Area prevTarget;

    @Autowired
    Logger logger;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    DevelopData developData;

    //Tools
    // Tools
    @Autowired SearchPathTool searchPathTool;
    @Autowired StuckInfoTool stuckInfoTool;
    @Autowired AgentTargetAreaTool agentTargetAreaTool;
    @Autowired HumanPositionTool humanPositionTool;
    @Autowired AreaPassableTool areaPassableTool;
    @Autowired PathUtilTool pathUtilTool;
    @Autowired
    PfTargetProviderTest targetProvider;
    @Autowired PfStuckEscapeTool pfStuckEscapeTool;
    @Autowired
    PfMediator pfMediator;
    @Autowired UnavailableTargetTool unavailableTargetTool;
    @Autowired AgentRestTool agentRestTool;

    @Override
    @After({"AgentRestTool","PfStuckEscapeTool"})
    public void calc(CalcContext context) {

        boolean continuePrev = false;
        if (prevTarget != null) {
            getAction(prevTarget, context); // 而且能从前一个目标中提取出动作
            if (context.getResultAction() != null) {
                if (!pfMediator.isAllowed(prevTarget)) { // 竞争失败
                    logger.info("Someone better suited to this target than me. give up.");
                } else continuePrev = true;
            } else {
                prevTarget = null;
            }
        }

        // 获取排序好后的候选
        if (!continuePrev) {
            var sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
            logger.debug("sortedTargetsAndScores" + sortedTargetsAndScores);
            // 按由好到坏的顺序，尝试从候选 area 中提取动作，返回第一个非空动作
            for (var t : sortedTargetsAndScores) {
                Area target = t.e0;
                double score = t.e1;
                context.clear();
                if (!pfMediator.isAllowed(target)) { // 竞争失败
                    logger.info("Someone better suited to this target than me. give up.");
                    continue;
                }
                context.setResultTarget(target);
                context.setResultScore(score);
                getAction(target, context);
                if (context.getResultAction() != null) { // 计算出有效动作
                    break;
                } else {
                    unavailableTargetTool.setUnavailableAndWait(
                            target.getID(),
                            UnavailableType.pfNoAction,
                            params.getInt("waitAll"),
                            true);
                    logger.info("Cannot extract an action to do target " + target);
                }
            }

//            // 调试器上，所有目标着浅红色，并用橙色显示其排序
//            var isCandidateLayer = HFUTDebugger.getMiscLayerOf(agentInfo, IsCandidateLayer.class);
//            var candidateRankLayer = HFUTDebugger.getMiscLayerOf(agentInfo, CandidateRankLayer.class);
//            if (isCandidateLayer != null && candidateRankLayer != null) {
//                for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
//                    Area candidate = sortedTargetsAndScores.get(i).e0;
//                    isCandidateLayer.addShape(candidate.getShape(), OptionsTool.T_RED);
//                    if(i <= 5)
//                        candidateRankLayer.addText(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED);
//                    else
//                        candidateRankLayer.addText(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.ORANGE);
//                }
//            }
//        }

            logger.info(context);
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        if (context.getTarget() != null)
            this.prevTarget = context.getTarget();
        logger.info("prevTarget: " + prevTarget);
    }


    @Nonnull
    private List<LineSegment> getCheckLines(final @Nonnull List<HFUTEdge> edgePath) {
        assert edgePath.size() > 1;

        List<LineSegment> checkLineList = new ArrayList<>();
        Iterator<HFUTEdge> iter1 = edgePath.listIterator();
        Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
        while (iter2.hasNext()) {
            HFUTEdge e1 = iter1.next();
            HFUTEdge e2 = iter2.next();
            Coordinate c1 = HFUTGeometryTool.getMidPointCoordinate(e1);
            Coordinate c2 = HFUTGeometryTool.getMidPointCoordinate(e2);
            LineSegment ls = new LineSegment(c1, c2);
            checkLineList.add(ls);
        }

        return checkLineList;
    }

    @Nonnull
    private Geometry getCheckPolygon(
            final @Nonnull Area area,
            final @Nonnull LineSegment checkLine
    ) {

        LineString ls = GF.createLineString(new Coordinate[]{ checkLine.p0, checkLine.p1 });
        Geometry rawCheckPolygon = ls.buffer(OptionsTool.PF_CHECK_RADIUS, 1, BufferParameters.CAP_FLAT);

        // 计算裁剪后的检测区域
        Geometry areaPolygon = ToJts.fromRcrsArea(area);
        Geometry croppedCheckPolygon;
        try {
            croppedCheckPolygon = rawCheckPolygon.intersection(areaPolygon);
        } catch (TopologyException e) { // 如果出现 TopologyException，则修复后再次尝试
            areaPolygon = GeometryFixer.fix(areaPolygon);
            croppedCheckPolygon = rawCheckPolygon.intersection(areaPolygon);
        }

        return croppedCheckPolygon;
    }

    @Nullable
    private Tuple2<Coordinate, Vector2D> getGuideCoordAndGuideVector(
            final @Nonnull Coordinate meCoord,
            final @Nonnull LineSegment checkLine,
            final @Nullable Geometry blockadePolygon,
            final @Nonnull Geometry checkPolygon
    ) {

        if (blockadePolygon == null || blockadePolygon.isEmpty())
            return null;


//        MiscLayer layer = HFUTDebugger.getMiscLayerOf(agentInfo, PfActionLayer.class);
//        Geometry _blockadePolygon = blockadePolygon.buffer(20, 1);
//        Geometry intersectedBlockades;
//        try {
//            intersectedBlockades = _blockadePolygon.intersection(checkPolygon);
//            Coordinate[] coordinates = intersectedBlockades.getCoordinates();
//            Coordinate[] coordinates1 = checkPolygon.getCoordinates();
//            if(layer != null){
//
//                for(int i = 0;i < coordinates.length - 1;i++)
//                    layer.addLine(coordinates[i].getX(), coordinates[i].getY(),
//                            coordinates[i + 1].getX(), coordinates[i + 1].getY(),
//                            Color.RED);
//                for(int i = 0;i < coordinates1.length - 1;i++)
//                    layer.addLine(coordinates1[i].getX(), coordinates1[i].getY(),
//                            coordinates1[i+1].getX(), coordinates1[i+1].getY(),
//                            Color.PINK);
//            }
//        } catch (TopologyException e) {
//            Geometry checkPolygonFixed = GeometryFixer.fix(checkPolygon);
//            Geometry blockadePolygonFixed = GeometryFixer.fix(_blockadePolygon);
//            intersectedBlockades = blockadePolygonFixed.intersection(checkPolygonFixed);
//        }
        // 没有障碍，检测下一块
        if (intersectedBlockades == null || intersectedBlockades.isEmpty()) {
            return null;
        }

        logger.info("intersectedBlockades: " + intersectedBlockades);

        // 在我检测范围内所有障碍物顶点
        Coordinate[] blockadeCoordinates = intersectedBlockades.getCoordinates();
        // 找出离我最 “近” 的一个障碍物顶点作为 guideCoord
        Coordinate guideCoord = null;
        double minProjFactor = Double.MAX_VALUE;
        double meProjFactor = checkLine.projectionFactor(meCoord);
        boolean allBack = true;
        for (Coordinate c : blockadeCoordinates) {
            double projFactor = checkLine.projectionFactor(c);
            if (projFactor > meProjFactor // 忽略我身后的障碍点
                    && projFactor < minProjFactor) {
                allBack = false;
                guideCoord = checkLine.project(c);
                minProjFactor = projFactor;
            }
        }
        // 如果障碍物全在身后，则检测下一块
        if(allBack)
            return null;

        // 找到了 guideCoord，计算 guideVector
        double dx = checkLine.p1.x - checkLine.p0.x;
        double dy = checkLine.p1.y - checkLine.p0.y;
        Vector2D guideVector = new Vector2D(dx, dy)
                .normalised()
                .scale(OptionsTool.REPAIR_DISTANCE);

        return Tuple2.of(guideCoord, guideVector);
    }

    @Nonnull
    private Stream<Tuple2<Double, Double>> cleanAngleRange(
            final @Nonnull Coordinate meCoord,
            final @Nonnull Coordinate targetCoord
    ) {
        Coordinate c2 = new CoordinateXY((meCoord.x + targetCoord.x) / 2.0, (meCoord.y + targetCoord.y) / 2.0);
        double r2 = meCoord.distance(targetCoord) / 2.0;
        List<Coordinate> intersections = HFUTGeometryTool.circleIntersection(
                meCoord, c2, OptionsTool.REPAIR_RADIUS, r2);

//        // 两个圆用灰色标记，交点用灰色叉标记
//        // TODO 换用新的 api
//        MiscLayer layer = HFUTDebugger.getMiscLayerOf(agentInfo);
//        if (layer != null) {
//            Ellipse2D e1 = new Ellipse2D.Double(
//                    meCoord.x - OptionsTool.REPAIR_RADIUS, meCoord.y - OptionsTool.REPAIR_RADIUS,
//                    2.0 * OptionsTool.REPAIR_RADIUS, 2.0 * OptionsTool.REPAIR_RADIUS);
//            Ellipse2D e2 = new Ellipse2D.Double(c2.x - r2, c2.y - r2, 2.0 * r2, 2.0 * r2);
//            layer.addShape(e1, OptionsTool.T_BLACK)
//                    .addShape(e2, OptionsTool.T_BLACK);
//            intersections.forEach(i -> layer.addPointCross(i, OptionsTool.T_BLACK, 300));
//        }

        double angle1, angle2;
        double angleMid = normalizePositive(angle(meCoord, targetCoord));

        // 如果找不到两个交点，说明我到目标的距离小于清理半径，正面任何方向都能清理到
        if (intersections.size() != 2) {
            angle1 = normalizePositive(angleMid - Math.PI / 2. + Math.PI / 50.); // TODO 3.0 会不会更好
            angle2 = normalizePositive(angleMid + Math.PI / 2. - Math.PI / 50.);
        } else {
            Coordinate i1 = intersections.get(0);
            Coordinate i2 = intersections.get(1);

            angle1 = normalizePositive(angle(i1, targetCoord));
            angle2 = normalizePositive(angle(i2, targetCoord));
        }

        return splitRange(angle1, angle2);
    }

    /**
     * 如果 angle1-angle2 跨过了 0度 / 360度，则将其划分为两个角
     * @apiNote [30, 350] => [0, 30] + [350, 360]
     *          <br>
     *          [350, 20] => [0, 20] + [350, 360]
     *          <br>
     *          [90, 210] => (不划分)
     */
    private Stream<Tuple2<Double, Double>> splitRange(
            final double angle1,
            final double angle2
    ) {
        if (HFUTGeometryTool.isBetween(angle1, angle2, 0.)) {
            if (angle1 < angle2)
                return Stream.of(
                        Tuple2.of(0., angle1),
                        Tuple2.of(angle2, Math.PI * 2.)
                );
            else return Stream.of( //// finished
                    Tuple2.of(0., angle2),
                    Tuple2.of(angle1, Math.PI * 2.)
            );
        } else {
            if (angle1 < angle2)
                return Stream.of(Tuple2.of(angle1, angle2));
            else return Stream.of(Tuple2.of(angle2, angle1));
        }
    }

}
