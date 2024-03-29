package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.police.ActionClear;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.Tuple;
import hfutinvincible.Tuple2;
import hfutinvincible.Tuple3;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.MiscLayer;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.*;
import hfutinvincible.tool.area.ToJts;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.AgentTargetAreaTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.mediator.PfMediator;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.PfTargetProvider;
import org.apache.log4j.Logger;
import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.buffer.BufferParameters;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.locationtech.jts.algorithm.Angle.angle;
import static org.locationtech.jts.algorithm.Angle.normalizePositive;
import static org.locationtech.jts.algorithm.Angle.*;

@HfutTool
@AgentType({PoliceForce.class})
public class PfExecutor implements LifeCycle {
    private static final GeometryFactory GF = new GeometryFactory();

    /** 前一个周期的引导点坐标（可能为空） */

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
    PfTargetProvider targetProvider;
    @Autowired
    PfMediator pfMediator;
    @Autowired UnavailableTargetTool unavailableTargetTool;
    @Autowired AgentRestTool agentRestTool;
    private List<Tuple2<Area,Double>> sortedTargetsAndScores;
    private Params params = new Params(getClass().getSimpleName());

    @Override
    @After({"AgentRestTool"})
    public void calc(CalcContext context) {
        if (agentInfo.getTime() <scenarioInfo.getKernelAgentsIgnoreuntil()){
            return;
        }
        logger.info("------------------This is the"+agentInfo.getTime()+"time Pf Move------------------");
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
            this.sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
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
            logger.info(context);
//            // 调试器上，所有目标着浅红色，并用橙色显示其排序
            Painter<?> isCandidatepainter = Debugger.getPainterOfAgent(agentInfo);
            Painter<?> candidateRankpainter = Debugger.getPainterOfAgent(agentInfo);
            if (isCandidatepainter != null && candidateRankpainter != null) {
                for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
                    if(i < 3){
                        Area candidate = sortedTargetsAndScores.get(i).e0;
                        isCandidatepainter.addPaintables("IsCandidateLayer",false,
                                Paintable.shape(candidate.getShape(), OptionsTool.T_RED,true));
                        candidateRankpainter.addPaintables("CandidateRankLayer",false,
                                Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED));
                    } else {
                        Area candidate = sortedTargetsAndScores.get(i).e0;
                        isCandidatepainter.addPaintables("IsCandidateLayer",false,
                                Paintable.shape(candidate.getShape(), OptionsTool.T_RED,true));
                        candidateRankpainter.addPaintables("CandidateRankLayer",false,
                                Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.ORANGE));
                    }
                }
            }
            params.registerParamsChangedListener(()->{
                updateTargets();
                PaintCandidatesAndRank();
            });

        }
    }

    /**
     * 更新目标*/
    private void updateTargets(){
        Debugger.removeNonPersistPaintables(agentInfo);
        Debugger.registerOrRefreshAgent(worldInfo,agentInfo,scenarioInfo);
        this.sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
    }
    //显示目标
    private void PaintCandidatesAndRank(){
        // 调试器上，所有目标着浅红色，并用橙色显示其排序
        Painter<?> isCandidatepainter = Debugger.getPainterOfAgent(agentInfo);
        Painter<?> candidateRankpainter = Debugger.getPainterOfAgent(agentInfo);
        if (isCandidatepainter != null && candidateRankpainter != null) {
            logger.info("-------------Time---- "+agentInfo.getTime()+"-----"+sortedTargetsAndScores);
            for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
                if(i < 3){
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(),OptionsTool.T_BLUE,true));
                    candidateRankpainter.addPaintables("CandidateRankLayer",false,
                            Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED));
                }
                else{
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(),OptionsTool.T_BLUE,true));
                    candidateRankpainter.addPaintables("CandidateRankLayer",false,
                            Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.ORANGE));
                }
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        if (context.getTarget() != null)
            this.prevTarget = context.getTarget();
        logger.info("prevTarget: " + prevTarget);
        logger.info("---------------------Time"+agentInfo.getTime()+" pf action end--------------------");
//        //SearchPathTool Debug
        Painter<?> searchPathpainter = Debugger.getPainterOfAgent(agentInfo);
        if (searchPathpainter != null){
            Area target = context.getTarget();
            if (target != null){
                Path path = searchPathTool.setFromAndTo(agentInfo.getPositionArea(),target)
                        .calc()
                        .getAreaPath();
                if (path != null){
                    for (Area area:path.getRoadList()) {
                        searchPathpainter.addPaintables("PfSearchPathDebug",false,
                                Paintable.shape(area.getShape(),Paintable.addAlpha(Color.ORANGE,40),true));
                    }
                }
            }
        }
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


        Painter<?> layer = Debugger.getPainterOfAgent(agentInfo);
        Geometry _blockadePolygon = blockadePolygon.buffer(20, 1);
        Geometry intersectedBlockades;
        try {
            intersectedBlockades = _blockadePolygon.intersection(checkPolygon);
            Coordinate[] coordinates = intersectedBlockades.getCoordinates();
            Coordinate[] coordinates1 = checkPolygon.getCoordinates();
            if(layer != null){

                for(int i = 0;i < coordinates.length - 1;i++)
                    layer.addPaintables("PfExecutorActionLayer",false,
                            Paintable.line(coordinates[i].getX(), coordinates[i].getY(),
                                    coordinates[i + 1].getX(), coordinates[i + 1].getY(),
                                    Color.RED));
                for(int i = 0;i < coordinates1.length - 1;i++)
                    layer.addPaintables("PfExecutorActionLayer",false,
                            Paintable.line(coordinates1[i].getX(), coordinates1[i].getY(),
                                    coordinates1[i+1].getX(), coordinates1[i+1].getY(),
                                    Color.PINK));
            }
        } catch (TopologyException e) {
            Geometry checkPolygonFixed = GeometryFixer.fix(checkPolygon);
            Geometry blockadePolygonFixed = GeometryFixer.fix(_blockadePolygon);
            intersectedBlockades = blockadePolygonFixed.intersection(checkPolygonFixed);
        }
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
            else return Stream.of( //// resolve
                    Tuple2.of(0., angle2),
                    Tuple2.of(angle1, Math.PI * 2.)
            );
        } else {
            if (angle1 < angle2)
                return Stream.of(Tuple2.of(angle1, angle2));
            else return Stream.of(Tuple2.of(angle2, angle1));
        }
    }

    /**
     * 指定的在我清理范围内的人，计算出一个动作去救他们
     * @param myCoord 我的坐标
     * @param humansToRescue 在我清理范围内的要救的人
     * @param pathAngle 路径的角度，计算出的清理角度将尽可能和此角度接近
     */
    @Nonnull
    private ActionClear getActionToRescueStuckHumanWithinMyRepairDistance(
            final @Nonnull Area myPosArea,
            final @Nonnull Coordinate myCoord,
            final @Nonnull Set<Human> humansToRescue,
            final double pathAngle,
            final @Nonnull Geometry blockadePolygon
    ) {
        // 获得所有事件
        List<Tuple2<Double, Boolean>> events = humansToRescue.stream()
                .map(human -> new Coordinate(human.getX(), human.getY()))
                .flatMap(humanCoord -> cleanAngleRange(myCoord, humanCoord))
                .flatMap(pair -> Stream.of(
                        Tuple2.of(pair.e0, true),
                        Tuple2.of(pair.e1, false)
                ))
                .sorted(Comparator.comparingDouble(t -> t.e0)) // 按角度排序
                .toList();

        int[] cntArr = new int[events.size()];
        int cnt = 0;
        for (int j = 0; j < events.size(); j++) {
            Tuple2<Double, Boolean> event = events.get(j);
            if (event.e1) cntArr[j] = ++cnt; // 一个 Human 进入清理范围
            else cntArr[j] = --cnt; // 一个 Human 超出清理范围
        }
        // 找出第几个 event 所在角度能覆盖最多的清理范围
        int maxCount = Integer.MIN_VALUE;
        for (int curCount : cntArr) {
            if (curCount > maxCount)
                maxCount = curCount;
        }

        // 计算所有候选清理角度（以 PI / 16. 为间距采样）
//        MiscLayer pfActionLayer = HFUTDebugger.getMiscLayerOf(agentInfo, PfActionLayer.class);
        boolean isPathAngleCandidate = false; // 路径倾角是否可做为候选
        List<Double> candidateCleanAngles = new ArrayList<>();
        for (int j = 0; j < cntArr.length; j ++) {
            if (cntArr[j] == maxCount) {
                double fromAngle = events.get(j).e0;
                double toAngle = events.get(j + 1).e0;
//                if (pfActionLayer != null)
//                    pfActionLayer.addArc(myCoord.x, myCoord.y, fromAngle, toAngle, OptionsTool.T_BLUE);
                if (HFUTGeometryTool.isBetween(fromAngle, toAngle, pathAngle))
                    isPathAngleCandidate = true;
                candidateCleanAngles.addAll(sampleCleanAngle(fromAngle, toAngle, Math.PI / 16.));
            }
        }

        // 先看看 path angle 是不是一个合适的选择（能清理到障碍）
        if (isPathAngleCandidate) {
            Vector2D cleanVector = new Vector2D(Math.cos(pathAngle), Math.sin(pathAngle))
                    .normalised()
                    .scale(OptionsTool.REPAIR_DISTANCE);
            Coordinate endCoord = new CoordinateXY(myCoord.x + cleanVector.getX(), myCoord.y + cleanVector.getY());
            Coordinate firstStuckCoord = areaPassableTool.getFirstStuckCoord(myCoord, endCoord, blockadePolygon);
            if (firstStuckCoord != null
                    && myCoord.distance(firstStuckCoord) < OptionsTool.REPAIR_DISTANCE * 0.8)
                return new ActionClear(agentInfo, cleanVector);
        }

        logger.info(candidateCleanAngles);

        // 否则选择第一个卡住我的点距离我最近的一个清理方向
        Vector2D cleanVector = candidateCleanAngles.stream()
                .map(angle -> new Vector2D(Math.cos(angle), Math.sin(angle))
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE))
                .min(Comparator.comparingDouble(vec -> {
                    Coordinate endCord = new CoordinateXY(myCoord.x + vec.getX(), myCoord.y + vec.getY());
                    Coordinate firstStuckCoord = areaPassableTool.getFirstStuckCoord(myCoord, endCord, blockadePolygon);
//                    if (pfActionLayer != null && firstStuckCoord != null)
//                        pfActionLayer.addPointCross(
//                                (int) firstStuckCoord.x, (int) firstStuckCoord.y, OptionsTool.T_BLACK, 200);
                    return firstStuckCoord == null
                            ? Double.MAX_VALUE
                            : myCoord.distance(firstStuckCoord);
                }))
                .orElseThrow();

        return new ActionClear(agentInfo, cleanVector);
    }

    /**
     * 根据指定的出发角度和目标角度，按指定间隔采样
     * @param fromAngle 出发角度
     * @param toAngle 目标角度
     * @param delta 采样间隔
     * @apiNote 出发角度必须小于目标角度。<br>
     *          例子： fromAngle=0, toAngle=40, delta = 7 => [0, 7, 14, 21, 28, 35, 40]
     */
    @Nonnull
    private List<Double> sampleCleanAngle(double fromAngle, double toAngle, double delta) {
        List<Double> samples = new ArrayList<>();
        for (double i = fromAngle; i < toAngle; i += delta)
            samples.add(i);
        samples.add(toAngle);
        return samples;
    }

    /**
     * 指定和我同一个 area，但不在我清理范围内的人，计算出一个动作移动到他们附近
     * @param humansToRescue 和我同一个 area，但不在我清理范围内的人
     * @implNote 目前是直接选一个人，然后移动到他旁边。
     */
    @Nonnull
    private Action getActionToGetClostToStuckHumansInMyAreaButOutOfMyRepairDistance(
            final @Nonnull Area myPosArea,
            final @Nonnull Coordinate myCoord,
            final @Nonnull Set<Human> humansToRescue,
            final @Nonnull Geometry blockadePolygon
    ) {
        // 找出据离我最近的
        Human closestHuman = humansToRescue.stream()
                .min(Comparator.comparingDouble(human -> {
                    Coordinate humanCoord = new CoordinateXY(human.getX(), human.getY());
                    return myCoord.distance(humanCoord);
                }))
                .orElseThrow();
        Coordinate closestHumanCoord = new CoordinateXY(closestHuman.getX(), closestHuman.getY());
        Coordinate firstStuckCoord = areaPassableTool.getFirstStuckCoord(myCoord, closestHumanCoord, blockadePolygon);
        if (firstStuckCoord != null) { // 无法移动到 closestHuman
            Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
            painter.addPaintables("PfExecutor", false,
                    Paintable.pointcross(firstStuckCoord.x, firstStuckCoord.y, 100, Color.RED),
                    Paintable.text(firstStuckCoord.x + 100, firstStuckCoord.y + 100, "firstStuckCoord", Color.RED));
            logger.info("I cannot move to closestHuman " + closestHuman + ", coord=" + closestHumanCoord);
            double distanceToFirstStuckCoord = firstStuckCoord.distance(myCoord);
            logger.debug("distanceToFirstStuckCoord=" + distanceToFirstStuckCoord + ", REPAIR_DISTANCE=" + OptionsTool.REPAIR_DISTANCE);
            if (distanceToFirstStuckCoord < 1.0) {
                logger.info("firstStuckCoord is very close to me, clear to stuck human directly");
                double dx = closestHumanCoord.x - myCoord.x;
                double dy = closestHumanCoord.y - myCoord.y;
                Vector2D cleanVector = new Vector2D(dx, dy)
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE);
                return new ActionClear(agentInfo, cleanVector);
            } else if (distanceToFirstStuckCoord < OptionsTool.REPAIR_DISTANCE * 0.6) { // 第一个卡住我的点在我的清理范围内
                logger.info("firstStuckCoord is within my clear distance, clear to it");
                double dx = firstStuckCoord.x - myCoord.x;
                double dy = firstStuckCoord.y - myCoord.y;
                Vector2D cleanVector = new Vector2D(dx, dy)
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE);
                return new ActionClear(agentInfo, cleanVector);
            } else { // 第一个卡住我的点不在清理范围内，则移动过去
                logger.info("firstStuckCoord is out of my clear distance, move to get closer");
                return new ActionMove(List.of(myPosArea.getID()), (int) firstStuckCoord.getX(), (int) firstStuckCoord.getY());
            }
        }
        // 否则可以直接移动到 closestHuman
        return new ActionMove(List.of(myPosArea.getID()), closestHuman.getX(), closestHuman.getY());
    }

    /**
     * 计算清理路径
     * @param myPosArea 我所在的 area
     * @param targetArea 目标 area
     * @return 一个 Tuple3，第一个元素为 area 路径，第二个元素为边路径，第三个元素为倾角列表。注意 area 路径长为 n，则边路径长为 n + 1
     */
    @Nullable
    private Tuple3<List<Area>, List<HFUTEdge>, List<Double>> getCleanPath(
            final @Nonnull Area myPosArea,
            final @Nonnull Area targetArea
    ) {
        Path x = searchPathTool.setFromAndTo(myPosArea, targetArea)
                .ignoreBlockades()
                .calc()
                .getAreaPath();
        if (x == null || x.isEmpty())
            return null;

        LinkedList<Area> areaPath = x.getRoadList();
        List<EntityID> areaIdPath = x.getIDList();
        LinkedList<HFUTEdge> edgePath = pathUtilTool.areaPath2edgePath(areaPath);

        // 尝试找出我经过的前一个 area，以得到准确的出发边信息
        EntityID prevPosAreaId = null;
        for (int i = 1; i <= 10; i ++) {
            Action a = agentInfo.getExecutedAction(-i);
            if (a instanceof ActionMove am) {
                List<EntityID> movePath = am.getPath();
                int meIndex = movePath.indexOf(myPosArea.getID());
                if (meIndex > 0) {
                    prevPosAreaId = movePath.get(meIndex - 1);
                    break;
                }
            }
        }
        Area prevHintArea = null;
        if (prevPosAreaId != null && !areaIdPath.contains(prevPosAreaId)) { // 第二个条件防止走回头路
            Area mePrevPosArea = (Area) worldInfo.getEntity(prevPosAreaId);
            Area pathStartArea = areaPath.getFirst();
            if (mePrevPosArea != null &&
                    !areaPath.getFirst().equals(mePrevPosArea) &&
                    pathStartArea.getEdgeTo(mePrevPosArea.getID()) != null) {
                prevHintArea = mePrevPosArea;
            }
        }

        // 如果目标 area 不是 builiding，但是和一个 building 相邻，则目标应该为此 building
        // 以此计算得到准确的目标边信息
        Area lastArea = areaPath.getLast();
        Area lastHintArea = null;
        if (!(lastArea instanceof Building)) {
            for (EntityID neighborId : lastArea.getNeighbours()) {
                StandardEntity neighborEntity = worldInfo.getEntity(neighborId);
                if (neighborEntity instanceof Building building) {
                    lastHintArea = building; // 更新 endArea
                    break;
                }
            }
        }

        // 找到了满足要求 area，据其计算出出发边
        if (prevHintArea != null) {
            Edge fromEdge = prevHintArea.getEdgeTo(myPosArea.getID());
            if (fromEdge == null)
                return null;
            edgePath.addFirst(new HFUTEdge(fromEdge));
        } else {
            if (edgePath.size() > 0) { // 否则直接用 getOppositeEdge 计算出出发边
                HFUTEdge firstEdge = edgePath.getFirst();
                Edge fromEdge = HFUTGeometryTool.getOppositeEdge(myPosArea, firstEdge.getEdge());
                if (fromEdge == null)
                    return null;
                edgePath.addFirst(new HFUTEdge(fromEdge));
            }
        }

        // 找到了满足要求 area，据其计算出目标边
        if (lastHintArea != null) {
            Edge toEdge = lastArea.getEdgeTo(lastHintArea.getID());
            if (toEdge == null)
                return null;
            edgePath.addLast(new HFUTEdge(toEdge));
        } else {
            if (edgePath.size() > 0) { // 否则直接用 getOppositeEdge 计算目标边
                HFUTEdge lastEdge = edgePath.getLast();
                Edge toEdge = HFUTGeometryTool.getOppositeEdge(lastArea, lastEdge.getEdge());
                if (toEdge == null)
                    return null;
                edgePath.addLast(new HFUTEdge(toEdge));
            }
        }

        // areaPath 中只有一个 area，导致 edgePath 中没有 edge
        // 而且也没法通过 hintArea 推断出出发边或目标边
        if (edgePath.size() == 0) {
            List<HFUTEdge> hfutEdges = areaPassableTool.getHfutEdgesOf(myPosArea.getID());
            if (hfutEdges == null || hfutEdges.size() < 2) // 没有足够的边
                return null;
            // 选第一条边为出发边，使用 getOppositeEdge 推断对边
            HFUTEdge edge = hfutEdges.get(0);
            Edge otherEdge = HFUTGeometryTool.getOppositeEdge(myPosArea, edge.getEdge());
            if (otherEdge == null)
                return null;
            edgePath.addFirst(edge);
            edgePath.addFirst(new HFUTEdge(otherEdge));
        }
        // areaPath 中只有一个 area，导致 edgePath 中没有 edge
        // 但是已经通过 hintArea 推断出了一条边，则用 getOppositeEdge 计算另一条边
        else if (edgePath.size() == areaPath.size()) {
            HFUTEdge edge = edgePath.getFirst();
            Edge otherEdge = HFUTGeometryTool.getOppositeEdge(myPosArea, edge.getEdge());
            if (otherEdge == null)
                return null;
            if (prevHintArea != null) // 猜出来的是出发边
                edgePath.addLast(new HFUTEdge(otherEdge));
            else edgePath.addFirst(new HFUTEdge(otherEdge)); // 猜出来的是目标边
        }

        List<Double> pathAngles = new ArrayList<>();
        Iterator<HFUTEdge> iter1 = edgePath.listIterator();
        Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
        while (iter2.hasNext()) {
            HFUTEdge e1 = iter1.next();
            HFUTEdge e2 = iter2.next();
            Coordinate midCoord1 = HFUTGeometryTool.getMidPointCoordinate(e1);
            Coordinate midCoord2 = HFUTGeometryTool.getMidPointCoordinate(e2);
            pathAngles.add(Angle.angle(midCoord1, midCoord2));
        }

        return Tuple3.of(areaPath, edgePath, pathAngles);
    }

    private void getAction(
            final @Nonnull Area targetArea,
            @Nonnull CalcContext context
    ) {
        Area myPosArea = humanPositionTool.getMyPositionArea();
        Coordinate myCoord = new CoordinateXY(agentInfo.getX(), agentInfo.getY());
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        Set<Human> stuckHumans = stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .filter(changedEntities::contains)
                .map(worldInfo::getEntity)
                .filter(e -> e instanceof Human)
                .filter(e -> !(e instanceof PoliceForce))
                .map(Human.class::cast)
                .filter(human -> human.isXDefined() && human.isYDefined()) // 之后要用到 x 和 y
                .collect(Collectors.toUnmodifiableSet());

        Tuple3<List<Area>, List<HFUTEdge>, List<Double>> xx = getCleanPath(myPosArea, targetArea);
        if (xx == null)
            return;
        List<Area> areaPath = xx.get(0);
        if (areaPath.isEmpty())
            return;
        List<EntityID> areaIdPath = areaPath.stream().map(Area::getID).toList();
        List<HFUTEdge> edgePath = xx.get(1);
        List<Double> pathAngles = xx.get(2);

//        ///////////// Debug
//        MiscLayer pfActionLayer = HFUTDebugger.getMiscLayerOf(agentInfo, PfActionLayer.class);
//        if (pfActionLayer != null) {
//            for (Area area : areaPath)
//                pfActionLayer.addShape(area.getShape(), OptionsTool.T_YELLOW);
//            for (HFUTEdge edge : edgePath)
//                pfActionLayer.addLine(edge.getLine(), Color.MAGENTA);
//        }


        Geometry[] blockades = changedEntities.stream()
                .map(areaPassableTool::getBlockadePolygonOf)
                .filter(Objects::nonNull)
                .toArray(Geometry[]::new);

        GeometryCollection gc = GF.createGeometryCollection(blockades);
        Geometry joinedBlockades = gc.union();

        boolean _isPrevSaveHuman = isPrevSaveHuman;
        isPrevSaveHuman = true;
        // 1. 如果有在清理范围内的被卡的人
        Set<Human> stuckHumansWithinMyRepairDistance = stuckHumans.stream()
                .filter(human -> {
                    Coordinate humanCoord = new CoordinateXY(human.getX(), human.getY());
                    return myCoord.distance(humanCoord) < OptionsTool.REPAIR_REACHABLE_THRESHOLD;
                })
                .collect(Collectors.toUnmodifiableSet());

        if (!stuckHumansWithinMyRepairDistance.isEmpty()) {
            logger.info("stuckHumansWithinMyRepairDistance=" + stuckHumansWithinMyRepairDistance + ", rescue them!");
            double pathAngleOfMyArea = pathAngles.get(0);
            ActionClear resultAction = getActionToRescueStuckHumanWithinMyRepairDistance(
                    myPosArea, myCoord, stuckHumansWithinMyRepairDistance, pathAngleOfMyArea, joinedBlockades);
            visualizeActionClear(resultAction);
            context.setResultAction(resultAction);
            return;
        }

        // 2. 如果有在我所在 area 里，但不在我清理范围内的人
        Set<Human> stuckHumansInMyAreaButOutOfMyRepairDistance = stuckHumans.stream()
                .filter(human -> {
                    EntityID humanId = human.getID();
                    EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(humanId);
                    return myPosArea.getID().equals(humanPosAreaId);
                })
                .collect(Collectors.toUnmodifiableSet());

        if (!stuckHumansInMyAreaButOutOfMyRepairDistance.isEmpty()) {
            logger.info("stuckHumansInMyAreaButOutOfMyRepairDistance=" + stuckHumansInMyAreaButOutOfMyRepairDistance + ", rescue them!");
            Action resultAction = getActionToGetClostToStuckHumansInMyAreaButOutOfMyRepairDistance(
                    myPosArea, myCoord, stuckHumansInMyAreaButOutOfMyRepairDistance, joinedBlockades);
            context.setResultAction(resultAction);
            if (resultAction instanceof ActionMove am)
                context.setTargetPath(am.getPath());
            return;
        }

        isPrevSaveHuman = false;
        // 3. 否则，计算出清理我所在 area 到 targetArea 之间的清理路径，并清理之
        List<LineSegment> checkLines = getCheckLines(edgePath);


        Painter<?> layer = Debugger.getPainterOfAgent(agentInfo);
        if (layer != null)
            for (LineSegment l : checkLines)
                layer.addPaintables("PfExecutorActionLayer",false,
                        Paintable.line(l.p0.getX(),l.p0.getY(),l.p1.getX(),l.p1.getY(),OptionsTool.RED));

        Coordinate guideCoord = null;
        Vector2D guideVector = null;
        double totalCheckLength = 0.0;
        int i;
        if(layer != null)
            for(i = 0; i < areaPath.size();i ++){
                LineSegment l =checkLines.get(i);
                layer.addPaintables("PfExecutorActionLayer",false,
                        Paintable.line(l.p0.getX(),l.p0.getY(),l.p1.getX(),l.p1.getY(), Color.BLUE));
            }


        for (i = 0; i < areaPath.size(); i ++) {
            Area area = areaPath.get(i);
            logger.info("current area id: " + area.getID());
            EntityID areaId = area.getID();
            if (totalCheckLength > OptionsTool.MOVING_DISTANCE + OptionsTool.LOOKING_AHEAD_DISTANCE // 已经检查了足够远，超出了我的移动距离太多，则不继续检查
                    || !changedEntities.contains(areaId) // 如果这个 area 超出了我的视野范围，则不继续检查，因为我对其上的障碍认知可能不是最新的
            ) break;
            LineSegment checkLine = checkLines.get(i);
            logger.info("current checkLine: " + checkLine);
            totalCheckLength += checkLine.getLength();
            Geometry checkPolygon = getCheckPolygon(area, checkLine);
            Geometry blockadePolygon = areaPassableTool.getBlockadePolygonOf(areaId);
            // 尝试在第 i 个 area 上计算引导点和引导向量
            Tuple x = getGuideCoordAndGuideVector(myCoord, checkLine, blockadePolygon, checkPolygon);
            if (x == null)
                continue;
            guideCoord = x.get(0);
            guideVector = x.get(1);
            if(layer != null){
                layer.addPaintables("PfExecutorActionLayer",false,
                        Paintable.pointcross((int)guideCoord.getX(),(int)guideCoord.getY(),300,Color.GREEN));
                layer.addPaintables("PfExecutorActionLayer",false,
                        Paintable.line(guideCoord.getX(), guideCoord.getY(), guideVector.getX() + guideCoord.getX(), guideVector.getY() + guideCoord.getY(), Color.PINK));
            }
            logger.info("in " + i + " turn : guide point found: Coord: " + guideCoord + "; guideVector" + guideVector);
            break;
        }

        // 没有找到引导点
        if (guideCoord == null) {
            // 如果清理路径所有 area 都被检查一遍，也没找到引导点，则在我看来这条路已经清理干净了
            boolean isCleanedFromMyPerspect = i == areaPath.size();
            // 清理路径上，第几个 area 开始有人被卡
            int firstStuckHumanIndex = Integer.MAX_VALUE;
            Human firstStuckHuman = null;
            for (Human stuckHuman : stuckHumans) {
                EntityID stuckHumanId = stuckHuman.getID();
                EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(stuckHumanId);
                if (humanPosAreaId == null)
                    continue;
                int stuckHumanIndex = areaIdPath.indexOf(humanPosAreaId);
                if (stuckHumanIndex == -1)
                    continue;
                if (stuckHumanIndex < firstStuckHumanIndex) {
                    firstStuckHumanIndex = stuckHumanIndex;
                    firstStuckHuman = stuckHuman;
                }
            }
            if (isCleanedFromMyPerspect // 如果我认为已经清理干净了
                    && params.getBoolean("earlyStopEnabled") // 启用早停策略
                    && firstStuckHuman == null) { // 清理路径上没有要救的人
                logger.info("Path " + areaIdPath + " is cleaned!");
                return;
            }
            else { // 否则向目标移动
                if (targetArea.equals(myPosArea)) // 已经到了目标 area
                    return;
                // 计算移动路径，根据第一个被卡的人所在位置做裁剪，保证不会跑过头，错过被卡住的人
                List<EntityID> movePath;
                if (firstStuckHuman == null)
                    movePath = areaIdPath;
                else movePath = areaIdPath.subList(0, Math.min(firstStuckHumanIndex + 1, areaIdPath.size()));
                // 判断我能否移动到目标
                // 找出我当前 area 和下一个 area 的邻接边，检测我当前所在位置和这条边的中点的连线是否和障碍物有交点
//                if (movePath.size() == 1) // IMPOSSIBLE BRANCH
//                    return;
//                Edge e = myPosArea.getEdgeTo(movePath.get(1));
//                if (e == null) // IMPOSSIBLE BRANCH
//                    return;
                HFUTEdge e = edgePath.get(1);
                Coordinate checkCoord = HFUTGeometryTool.getMidPointCoordinate(e);
                Coordinate firstStuckCoord = areaPassableTool.getFirstStuckCoord(myCoord, checkCoord, joinedBlockades);
                if (firstStuckCoord == null) { // 没有交点，认为可以移动过去
                    logger.info("Move to targetArea " + targetArea + " directly, uncroppedPath=" + areaIdPath);
                    ActionMove resultAction = firstStuckHuman == null
                            ? new ActionMove(movePath)
                            : new ActionMove(movePath, firstStuckHuman.getX(), firstStuckHuman.getY());
                    context.setResultAction(resultAction);
                    context.setTargetPath(resultAction.getPath());
                    return;
                }
                // 第一个卡住我的点在我的清理范围内
                double distanceToFirstStuckCoord = myCoord.distance(firstStuckCoord);
                if (distanceToFirstStuckCoord < 1.0) {
                    double dx = checkCoord.x - myCoord.x;
                    double dy = checkCoord.y - myCoord.x;
                    Vector2D cleanVector = new Vector2D(dx, dy)
                            .normalised()
                            .scale(OptionsTool.REPAIR_DISTANCE);
                    logger.info("firstStuckCoord is within my repair distance. But too close to me. Clean to checkCoord directly!");
                    Action resultAction = new ActionClear(agentInfo, cleanVector);
                    visualizeActionClear((ActionClear) resultAction);
                    context.setResultAction(resultAction);
                    return;
                } else if (distanceToFirstStuckCoord < OptionsTool.REPAIR_DISTANCE * 0.6) {
                    double dx = firstStuckCoord.x - myCoord.x;
                    double dy = firstStuckCoord.y - myCoord.y;
                    Vector2D cleanVector = new Vector2D(dx, dy)
                            .normalised()
                            .scale(OptionsTool.REPAIR_DISTANCE);
                    logger.info("firstStuckCoord is within my repair distance. Clean to firstStuckCoord directly!");
                    Action resultAction = new ActionClear(agentInfo, cleanVector);
                    visualizeActionClear((ActionClear) resultAction);
                    context.setResultAction(resultAction);
                    return;
                } else { // 否则，先尝试更接近 firstStuckCoord 一些
                    logger.info("GuideCoord is out of my repair distance.");
                    List<EntityID> targetPath = List.of(myPosArea.getID());
                    Action resultAction = new ActionMove(targetPath, (int) firstStuckCoord.getX() , (int) firstStuckCoord.getY());
                    context.setResultAction(resultAction);
                    context.setTargetPath(targetPath);
                    return;
                }
            }
        }

        // 找到了引导点
        double distanceToGuideCoord = guideCoord.distance(myCoord);
        // 已经足够接近引导点，开始清理
        if (distanceToGuideCoord < OptionsTool.CLOSE_ENOUGH_TO_GUIDE_POINT) {
            logger.info("Close enough to guidePoint (distance=" + distanceToGuideCoord + "), start clean...");
            Action resultAction = new ActionClear(agentInfo, guideVector);
            visualizeActionClear((ActionClear) resultAction);
            context.setResultAction(resultAction);
            return;
        }

        // 检查是否能移动到引导点
        // 因为引导点肯定在中心线上，因此如果我和引导点在同一个 area，直接检测我和引导点的连线是否有交点
        // 否则检测我和下一条边的连线是否有交点即可
        Coordinate checkCoord;
        if (i == 0) { // 我和引导点在同一个 area
            checkCoord = guideCoord;
        } else { // 我和引导点不在同一个 area
            HFUTEdge e = edgePath.get(1);
            checkCoord = HFUTGeometryTool.getMidPointCoordinate(e);
        }
        Coordinate firstStuckCoord = areaPassableTool.getFirstStuckCoord(myCoord, checkCoord, joinedBlockades);
        if (firstStuckCoord != null) { // 无法移动到 guideCoord
            logger.info("I cannot move to guideCoord");
            // 第一个卡住我的点在我的清理范围内
            double distanceToFirstStuckCoord = myCoord.distance(firstStuckCoord);
            if (distanceToFirstStuckCoord < 1.0) {
                double dx = guideCoord.x - myCoord.x;
                double dy = guideCoord.y - myCoord.y;
                Vector2D cleanVector = new Vector2D(dx, dy)
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE);
                logger.info("firstStuckCoord is within my repair distance. But too close to me. Clean to guideCoord directly!");
                Action resultAction = new ActionClear(agentInfo, cleanVector);
                visualizeActionClear((ActionClear) resultAction);
                context.setResultAction(resultAction);
                return;
            } else if (myCoord.distance(firstStuckCoord) < OptionsTool.REPAIR_DISTANCE * 0.6) {
                double dx = firstStuckCoord.x - myCoord.x;
                double dy = firstStuckCoord.y - myCoord.y;
                Vector2D cleanVector = new Vector2D(dx, dy)
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE);
                logger.info("firstStuckCoord is within my repair distance. Clean to firstStuckCoord directly!");
                Action resultAction = new ActionClear(agentInfo, cleanVector);
                visualizeActionClear((ActionClear) resultAction);
                context.setResultAction(resultAction);
                return;
            } else { // 否则，先尝试更接近 firstStuckCoord 一些
                logger.info("GuideCoord is out of my repair distance.");
                List<EntityID> targetPath = List.of(myPosArea.getID());
                Action resultAction = new ActionMove(targetPath, (int) firstStuckCoord.getX(), (int) firstStuckCoord.getY());
                context.setResultAction(resultAction);
                context.setTargetPath(targetPath);
                return;
            }
        } else logger.info("I can move to guideCoord.");

        // 判断是否复用上一周期的清理动作
        Coordinate _prevGuideCoord = prevGuideCoord;
        prevGuideCoord = guideCoord;
        if (!_isPrevSaveHuman && params.getBoolean("repeatCleanEnabled") && _prevGuideCoord != null) {
            Coordinate endCoord = new CoordinateXY(guideCoord.x + guideVector.getX(), guideCoord.y + guideVector.getY());
            double turnAngle = angleBetween(_prevGuideCoord, guideCoord, endCoord);
            Action prevAction = agentInfo.getExecutedAction(-1);
            if (Math.abs(turnAngle - 3.14) < 0.01 &&
                    myCoord.distance(guideCoord) < OptionsTool.REPEAT_CLEAN_FACTOR * OptionsTool.REPAIR_DISTANCE &&
                    prevAction instanceof ActionClear) {
                logger.info("Repeat cleaning... ");
                context.setResultAction(prevAction);
                return;
            }
        }
        // 移动到引导点
        List<EntityID> movePath = areaIdPath.subList(0, Math.min(i + 1, areaPath.size()));
        logger.info("Move to guideCoord. areaPath=" + areaPath + ", croppedPath=" + movePath);
        ActionMove resultAction = new ActionMove(movePath, (int) guideCoord.getX() , (int) guideCoord.getY());
        context.setResultAction(resultAction);
        context.setTargetPath(resultAction.getPath());
        return;
    }

    private void visualizeActionClear(@Nonnull ActionClear ac) {
        logger.info("add pf action layer");
        Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
        painter.addPaintables(
                "PfAction",
                false,
                Paintable.line(agentInfo.getX(), agentInfo.getY(), ac.getPosX(), ac.getPosY(), Color.BLUE)
        );
    }
}
