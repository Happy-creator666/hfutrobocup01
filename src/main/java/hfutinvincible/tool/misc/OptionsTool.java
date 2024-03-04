package hfutinvincible.tool.misc;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.debug.*;
import org.jblas.DoubleMatrix;
import rescuecore.debug.Debugger;
import rescuecore2.config.Config;
import rescuecore2.standard.view.*;
import rescuecore2.standard.view.AnimatedHumanLayer;

import java.awt.*;

public class OptionsTool {

    private OptionsTool() {}

    private static boolean initialized = false;

    /** 每个智能体每周期的思考时间 */
    public static int THINK_TIMEOUT;
    public static int HEARING_RANGE = 30000;

    /**  agent 半径 */
    public static final int AGENT_RADIUS = 500;
    /** 一条边的长度大于此值，才认为这条边可能能过人 */
    public static final int EDGE_PASSABLE_MIN_LENGTH = AGENT_RADIUS;
    public static final int PATH_COORD_ANGLE_DELTA = 2;
    /**  */
    public static final int PATH_COORD_IGNORE_THRESHOLD = 10;
    /**  AccessibleTool 知晓一个路障多少周期之后未收到更新则认为其已被清除 */
    public static final int AREA_BLOCKED_FORGET_TIME = 20;
    /**  当一个 Cross 内的 Road 个数超过该值时，对这个 Cross 进行二次寻路以提高寻路效果 */
    public static final int CROSS_INNERSEARCH_THRESHOLD = 3;
    /**  每个分区中平均几个 FB */
    public static final int FB_PER_PARTITION = 4;
    /**  对 FB 做 kmeans 聚类的收敛阀值 */
    public static final double FB_CONVERGE_THRESHOLD = 10.0;
    /**  对 FB 做 kmeans 聚类时最大迭代次数 */
    public static final int FB_MAX_ITERATION_TIMES = 10;
    /**  每个分区中平均分几个 PF */
    public static final int PF_PER_PARTITION = 5;
    /**  对 PF 做 kmeans 聚类的收敛阀值 */
    public static final double PF_CONVERGE_THRESHOLD = 10.0;
    /**  对 PF 做 kmeans 聚类时最大迭代次数 */
    public static final int PF_MAX_ITERATION_TIMES = 10;
    /**  每个分区中平均分几个 AT */
    public static final int AT_PER_PARTITION = 5;
    /**  对 AT 做 kmeans 聚类的收敛阀值 */
    public static final double AT_CONVERGE_THRESHOLD = 10.0;
    /**  对 AT 做 kmeans 聚类时最大迭代次数 */
    public static final int AT_MAX_ITERATION_TIMES = 10;
    /** 判断是否在移动时被卡住时路径长要乘的系数 */
    public static final double STUCK_WHEN_MOVING_THRESHOLD = 0.6;
    public static final int IGNORE_STUCK_HUMAN_AFTER = Integer.MAX_VALUE;
    /** 计算 AT 和 FB 的 max travel time 时要增加的量 */
    public static final int FB_AT_MAX_TRAVEL_TIME_DELTA = 0;
    /** 分区可视化时用的颜色 */
    public static final Color[] COLOURS = {
            new Color(255, 179, 0),     // Vivid Yellow
            new Color(128, 62, 117),    // Strong Purple
            new Color(255, 104, 0),     // Vivid Orange
            new Color(166, 189, 215),   // Very Light Blue
            new Color(193, 0, 32),      // Vivid Red
            new Color(206, 162, 98),    // Grayish Yellow
            new Color(129, 112, 102),   // Medium Gray
            new Color(0, 125, 52),      // Vivid Green
            new Color(246, 118, 142),   // Strong Purplish Pink
            new Color(0, 83, 138),      // Strong Blue
            new Color(255, 122, 92),    // Strong Yellowish Pink
            new Color(83, 55, 122),     // Strong Violet
            new Color(255, 142, 0),     // Vivid Orange Yellow
            new Color(179, 40, 81),     // Strong Purplish Red
            new Color(244, 200, 0),     // Vivid Greenish Yellow
            new Color(127, 24, 13),     // Strong Reddish Brown
            new Color(147, 170, 0),     // Vivid Yellowish Green
            new Color(89, 51, 21),      // Deep Yellowish Brown
            new Color(35, 44, 22),      // Dark Olive Green
    };

    public static Color randomColor() {
        return COLOURS[(int) (Math.random() * COLOURS.length)];
    }
    public static Color ithColor(int i) {
        return COLOURS[i % COLOURS.length];
    }

    /**  是否为调试模式，若是，则启动 HFUTDebugger 以供调试 */
    public static final boolean IS_DEBUG_MODE = true;
    /** 预计算时是否开启调试器，注意：应手动使用 scanner 等方法阻塞线程，否则调试器只会一闪而过 */
    public static final boolean IS_PRECOMPUTE_DEBUG_MODE = false;

    // TODO 在不同阶段，智能体的重要性也是不一样的。比如前期警察的效率十分重要，但后期救护消防更重要。因此下面三个救援阈值应该是关于比赛时间的函数。
    public static int AT_NEED_RESCUE_THRESHOLD = 50;
    public static int PF_NEED_RESCUE_THRESHOLD = 20;
    public static int FB_NEED_RESCUE_THRESHOLD = 50;

    /** 分区边缘提取时，将整个区间划分成几等分 */
    public static int RANGE_NUM = 30;
    /** 分区边缘提取时，每个区间左右两端各取几个数据点*/
    public static int SELECT_NUM = 2;
    /** 边缘提取时，每个区间每侧提取百分之多少的点 */
    public static double SELECT_PROPORTION = 0.08;
    /** 分区可达性检测时，搜索到的本分区建筑数到达此值，则认为可达 */
    public static final int PARTITION_ACCESSIBLE_THRESHOLD = 100; // XXX 可以优化
    /** 分区可达性检测时，判断不可达后，若搜索到的本分区建筑数达到此值，则认为需要重新聚类 */
    public static final int PARTITION_RECLUSTER_THRESHOLD = 50; // XXX 可以优化

    /** 一共模拟多少周期，默认模拟 300 个周期 */
    public static int KERNEL_TIMESTEPS = 300;
    /** PF 清障半径，为防止误差，已减去 100 */
    public static int REPAIR_RADIUS = 1600;
    /** PF 清障距离 */
    public static int REPAIR_DISTANCE;

    /** 两点间距离大于此值，则说明两点不能被认为是同一点 */
    public static int SAME_POINT_THRESHOLD = 100;
    /** 计算距离 refuge 入口的接近程度时，搜索深度不超过此值 */
    public static double REFUGE_ENTRANCE_STOP_PROPAGATION= 5;

    /** 计算距离建筑入口的接近程度时，搜索深度不超过此值 */
    public static int BUILDING_ENTRANCE_STOP_PROPAGATION = 5;

    /** 智能体的可视距离 */
    public static int VIEW_DISTANCE = 30000;
    /** 一个智能体发出的视线数量 */
    public static int RAY_COUNT = 72;
    /** 视线感知错误阀值 */
    public static int SIGHT_PERCEPTION_ERROR_THRESHOLD = 500;

    /** BlockadeTool 中，栅格化多边形时。每个格子的边长 */
    public static int RASTERIZING_DELTA = 100;

    /** 计算 C-free space 时，往外扩展的距离 */
    public static int BUFFER_DISTANCE = AGENT_RADIUS;

    /** 计算警察检测清理时，扩张的范围 */
    public static int PF_CHECK_RADIUS = REPAIR_RADIUS - 2 * AGENT_RADIUS;

    /** 对 PF，passable 的两条边，计算通行代价时乘的系数 */
    public static final double PF_PASSABLE_ROAD_FACTOR = 1;
    /** 对 PF，impassable 的两条边，计算通行代价时乘的系数 */
    public static final double PF_IMPASSABLE_ROAD_FACTOR = 10;
    /** 对 PF，unknown 的两条边，计算通行代价时乘的系数 */
    public static final double PF_UNKNOWN_ROAD_FACTOR = 1.2;
    /** 对 PF，损坏情况未知的建筑，计算通行代价时乘的系数 */
    public static final double PF_UNKNOWN_BUILDING_FACTOR = 5;
    /** 对 PF，未损坏的建筑，计算通行代价时乘的系数 */
    public static final double PF_UNBROKEN_BUILDING_FACTOR = 0.8;
    /** 对 PF，损坏的建筑，计算通行代价时乘的系数 */
    public static final double PF_BROKEN_BUILDING_FACTOR = 15;

    /** 对 AT，passable 的两条边，计算通行代价时乘的系数 */
    public static final double AT_PASSABLE_ROAD_FACTOR = 1;
    /** 对 AT，impassable 的两条边，计算通行代价时乘的系数 */
    public static final double AT_IMPASSABLE_ROAD_FACTOR = 10;
    /** 对 AT，unknown 的两条对 AT，边，计算通行代价时乘的系数 */
    public static final double AT_UNKNOWN_ROAD_FACTOR = 1.2;
    /** 对 AT，损坏情况未知的建筑，计算通行代价时乘的系数 */
    public static final double AT_UNKNOWN_BUILDING_FACTOR = 5;
    /** 对 AT，未损坏的建筑，计算通行代价时乘的系数 */
    public static final double AT_UNBROKEN_BUILDING_FACTOR = 0.8;
    /** 对 AT，损坏的建筑，计算通行代价时乘的系数 */
    public static final double AT_BROKEN_BUILDING_FACTOR = 15;

    /** 对 FB，passable 的两条边，计算通行代价时乘的系数 */
    public static final double FB_PASSABLE_ROAD_FACTOR = 1;
    /** 对 FB，impassable 的两条边，计算通行代价时乘的系数 */
    public static final double FB_IMPASSABLE_ROAD_FACTOR = 10;
    /** 对 FB，unknown 的两条边，计算通行代价时乘的系数 */
    public static final double FB_UNKNOWN_ROAD_FACTOR = 1.2;
    /** 对 FB，损坏情况未知的建筑，计算通行代价时乘的系数 */
    public static final double FB_UNKNOWN_BUILDING_FACTOR = 5;
    /** 对 FB，未损坏的建筑，计算通行代价时乘的系数 */
    public static final double FB_UNBROKEN_BUILDING_FACTOR = 0.8;
    /** 对 FB，损坏的建筑，计算通行代价时乘的系数 */
    public static final double FB_BROKEN_BUILDING_FACTOR = 15;

    /** 智能体每周体能移动的最大距离 */
    public static double MOVING_DISTANCE = 40000;

    public static double LOOKING_AHEAD_DISTANCE = 40000;

    /** 最多尝试几次绕路 */
    public static final int MAX_TRY_DETOUR_COUNT = 3;

    /** 找最优 Refuge 时，根据曼哈顿距离考虑最近的几个 Refuge */
    public static final int CONSIDER_REFUGES_NUM = 3;

    /** 默认掩埋深度 */
    public static final int DEFAULT_BURIEDNESS = 200; // TODO 需要调整
    /** 默认一个被埋的人能活多久 */
    public static final int DEFAULT_ACTIVE_TIME = 50; // 需要调整

    public static final int AT_MAX_COLLABORATE_AT = 2;
    public static final int AT_MAX_COLLABORATE_FB = 2;
    public static final double AT_MAX_SPOT_PASS_COST = 100000;
    public static final int AT_MAX_SPOT_RECEIVE_TIME = 5;

    /** AT 选 Spot 权重向量 (列向量) */
    public static final DoubleMatrix AT_SPOT_WEIGHTS = new DoubleMatrix(4, 1,
            0.2, 0.2, 0.4, 0.2);

    /** AT 选 Spot 权重向量 (列向量) */
    @Deprecated
    public static final DoubleMatrix AT_WEIGHTS = new DoubleMatrix(8, 1,
            0.15, 0.2, 0.3, 0.15, 0.05, 0.05, 0.05, 0.05);

    public static final int FB_MAX_SPOT_RECEIVE_TIME = 5;
    public static final int FB_MAX_COLLABORATE_FB = 2;
    public static final int FB_MAX_SPOT_PASS_COST = 100000;

    /** FB 选 Spot 权重向量 (列向量) */
    public static final DoubleMatrix FB_WEIGHTS = new DoubleMatrix(8, 1,
            0.15, 0.2, 0.3, 0.15, 0.05, 0.05, 0.05, 0.05);

    public static final Color DARK_GREEN = new Color(0, 100, 0);
    public static final Color BLUE = new Color(25, 25, 230);
    public static final Color RED = new Color(255, 0, 0);
    public static final Color T_BLACK = new Color(0, 0, 0, 50);
    public static final Color T_YELLOW = new Color(244, 200, 0, 50);
    public static final Color T_GREEN = new Color(0, 125, 52, 50);
    public static final Color T_RED = new Color(255, 0, 0, 50);
    public static final Color T_BLUE = new Color(25, 25, 230, 50);
    public static final Color TT_BLUE = new Color(25, 25, 230, 20);

    public static final int PF_MAX_SPOT_RECEIVE_TIME = 5;
    public static final int PF_MAX_COLLABORATE_PF = 2;
    public static final int PF_MAX_SPOT_PASS_COST = 100000;
    /** spotInfoMap 中 spot 的数量超过此值，则考虑删去失效的 spots */
    public static final int PF_DROP_EXPIRED_SPOTS_THRESHOLD = 50;

    /** PF 选 Spot 权重向量 (列向量) */
    public static final DoubleMatrix PF_SPOT_WEIGHTS = new DoubleMatrix(4, 1,
            0.2, 0.2, 0.4, 0.2);

    public static final DoubleMatrix PF_PATROL_TARGET_WEIGHTS = new DoubleMatrix(4, 1, 0.3, 0.2, 0.3, 0.2);

    public static final DoubleMatrix AT_PATROL_TARGET_WEIGHTS = new DoubleMatrix(1, 1, 1.0);

    public static final DoubleMatrix FB_PATROL_TARGET_WEIGHTS = new DoubleMatrix(1, 1, 1.0);

    /** PF 选 Refuge path 权重向量 (列向量) */
    public static final DoubleMatrix PF_REFUGE_PATH_WEIGHTS = new DoubleMatrix(1, 1, 1.0);


    /** 智能体到 guidePoint 的距离小于此值，则认为可以开始清理了 */
    public  static final double CLOSE_ENOUGH_TO_GUIDE_POINT = AGENT_RADIUS * 1.5;

    /** 我的引导线上有 {@link OptionsTool#REPAIR_DISTANCE} * {@code REPEAT_CLEAN_FACTOR} 前，使用前一周期的清理动作 */
    public static final double REPEAT_CLEAN_FACTOR = 0.66;
    /** 清障时保证以引导线为中心，左右各 REPAIR_CHECK_FACTOR 的距离内一定被清理干净 */
    public static final double REPAIR_CHECK_FACTOR = 0.8;
    /** 距离我此值以内的障碍皆可清除 */
    public static double REPAIR_REACHABLE_THRESHOLD;
    /** 计算可清理夹角时，向内缩的比例 */
    public static double ANGLE_FIX = 0.1;

    /** 一条边的长度小于此值，则不考虑此边 */
    public static final double EDGE_IGNORE_LENGTH = 300;

    /** 计算 Page Rank 时最多迭代多少次 */
    public static final int PAGE_RANK_NUM_ITERATIONS = 10;
    /** 计算 Page Rank 时，每次迭代使用不瞬移的概率 */
    public static final double PAGE_RANK_TELEPORT_PROBABILITY = 0.15;
    /** 计算 Spam Rank 时最多迭代多少次 */
    public static final int SPAM_RANK_NUM_ITERATIONS = 10;
    /** 计算 Spam Rank 时，每次迭代使用不瞬移的概率 */
    public static final double SPAM_RANK_TELEPORT_PROBABILITY = 0.15;
    /** 使用 Spam Rank 修正 Page Rank 时 Spam Rank 的权重*/
    public static final double SPAM_RANK_WEIGHT = 0.5;
    /** 最多转发一个 SpotTodoMessage 几次 */
    public static final int SPOT_TODO_MAX_FORWARD_TIMES = 3; // TODO 考虑时效性，是否需要再减小？
    /** Refuge 已满时, RefugeConditionMessage 最大转发次数 */
    public static final int REFUGE_FULL_MAX_FORWARD_TIMES = 5;
    /** Refuge 未满时, RefugeConditionMessage 最大转发次数 */
    public static final int REFUGE_FREE_MAX_FORWARD_TIMES = 2;
    /** 选择巡逻目标时，最多尝试几次 */
    public static final int AT_PATROL_MAX_TRY_COUNT = 10;

    public static final double TIME_TO_DEAD_A = 10;
    public static final double TIME_TO_DEAD_B = 30;
    public static final double TIME_TO_DEAD_C = 100;
    public static final double TIME_TO_DEAD_D = 0.2;
    /**
     * 如果不知道一个 Human 的 Time To Dead，用此值替换
     */
    public static final int TIME_TO_DEAD_UNKNOWN = KERNEL_TIMESTEPS;
    /**
     * 两次发送视野内一个人的 buriedness 之间至少间隔的周期数
     */
    public static final int SEND_HUMAN_BURIEDNESS_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个人的 buriedness 不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_HUMAN_BURIEDNESS_IN_SIGHT_DURATION = 5;
    /**
     * 两次发送视野内一个人的位置之间至少间隔的周期数
     */
    public static final int SEND_HUMAN_POS_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个人的位置不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_HUMAN_POS_IN_SIGHT_DURATION = 5;
    /**
     * 两次发送视野内一个人的 stuckType 之间至少间隔的周期数
     */
    public static final int SEND_STUCK_TYPE_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个人的 stuckType 不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_STUCK_TYPE_IN_SIGHT_DURATION = 5;

    /**
     * 两次发送视野内一个人的 stuckType 之间至少间隔的周期数
     */
    public static final int SEND_TIME_TO_DEAD_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个人的 stuckType 不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_TIME_TO_DEAD_IN_SIGHT_DURATION = 5;

    /**
     * 两次发送视野内 refuge 信息之间至少间隔的周期数
     */
    public static final int SEND_REFUGE_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个 refuge 不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_REFUGE_IN_SIGHT_DURATION = 5;

    /**
     * 两次发送 buildingBrokenness 信息之间至少间隔的周期数
     */
    public static final int SEND_BUILDING_BROKENNESS_IN_SIGHT_DURATION = 2;
    /**
     * 即使一个 buildingBrokenness 不发生变化，隔这么长的时间，还会重新发送一次 TODO 改成随时间指数增加的函数
     */
    public static final int RESEND_BUILDING_BROKENNESS_IN_SIGHT_DURATION = 5;

    /**
     * 如果一个 Refuge 的情况未知，则估计其空床位数为其床位总数乘以此因子
     */
    public static final double UNKNOWN_REFUGE_FREE_BEDS_ESTIMATE_FACTOR = 0.5;

    /**
     * 一个 Civilian 到障碍的距离小于此值，认为卡住了
     */
    public static final double CV_STUCK_DISTANCE = 100;

    /**
     * 一个 Agent 到障碍的距离小于此值，认为可能卡住
     */
    public static final double AGENT_CAN_BE_STUCK_DISTANCE = 100;

    ///////////////////////////// PfTargetProvider Params //////////////////////////

    public static final double PF_1 = 2;
    public static final double PF_1_6 = 0.3;
    public static final double PF_1_10 = 0.1;
    public static final double PF_1_11 = 0.1;
    public static final double PF_1_12 = 0.1;
    public static final double PF_1_13 = 0.1;
    public static final double PF_1_19 = 0.3;

    public static final double PF_0 = 1;
    public static final double PF_0_7 = 0.05;
    public static final double PF_0_1 = 0.2;
    public static final double PF_0_9 = 0.2;
    public static final double PF_0_10 = 0.05;
    public static final double PF_0_11 = 0.05;
    public static final double PF_0_12 = 0.05;
    public static final double PF_0_19 = 0.15;
    public static final double PF_0_5 = 0.05;
    public static final double PF_0_20 = 0.05;
    public static final double PF_0_21 = 0.01;
    public static final double PF_0_22 = 0.05;

    ///////////////////////////// AtTargetProvider Params //////////////////////////

    public static final double AT_1_2 = 1;
    public static final double AT_1_11 = 1;
    public static final double AT_1_5 = 1;

    /**
     * 一条声音信息最长多少个 bytes
     */
    public static int VOICE_MESSAGE_SIZE_IN_BYTE;

    public static void initialize(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        if (initialized) return;
        Config config = si.getRawConfig();
        int agentsNum = si.getScenarioAgentsAt() + si.getScenarioAgentsFb() + si.getScenarioAgentsPf();
//        THINK_TIMEOUT = (config.getIntValue("kernel.agents.think-time", 1000) / agentsNum) - 5; // TODO 是否考虑多线程执行？
        THINK_TIMEOUT = config.getIntValue("kernel.agents.think-time", 500);
        KERNEL_TIMESTEPS = config.getIntValue("kernel.timesteps", 300);
        REPAIR_RADIUS = config.getIntValue("clear.repair.rad") - 100;
        REPAIR_DISTANCE = config.getIntValue("clear.repair.distance", 10000);
        VIEW_DISTANCE = config.getIntValue("perception.los.max-distance", 30000);
        REPAIR_REACHABLE_THRESHOLD = 0.4 * Math.sqrt(REPAIR_RADIUS * REPAIR_RADIUS + REPAIR_DISTANCE * REPAIR_DISTANCE);
        VOICE_MESSAGE_SIZE_IN_BYTE = si.getVoiceMessagesSize();
        HEARING_RANGE = config.getIntValue("comms.channels.0.range", 30000);
        initialized = true;
    }
}