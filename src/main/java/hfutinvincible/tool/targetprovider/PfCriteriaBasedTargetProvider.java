package hfutinvincible.tool.targetprovider;

import hfutinvincible.anno.Autowired;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.criterion.AreaEdgePairPassableCriterion;
import hfutinvincible.tool.criterion.BuildingEntranceProximityCriterion;
import hfutinvincible.tool.criterion.PathToTargetCriterion;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;

import java.awt.geom.Area;

public class PfCriteriaBasedTargetProvider extends CriteriaBasedTargetProvider<Area> {
    @Autowired
    StaticPartitionTool staticPartitionTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    StuckInfoTool stuckInfoTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;

    // Criteria
    private AreaEdgePairPassableCriterion areaEdgePairPassableCriterion;
    private BuildingEntranceProximityCriterion buildingEntranceProximityCriterion;
    private PathToTargetCriterion pathToTargetCriterion;
    private RefugeEntranceProximityCriterion refugeEntranceProximityCriterion;
    private StuckHumanConditionCriterion stuckHumanConditionCriterion;
    private UnavailableCriterion unavailableCriterion;
    private FbAndAtsTargetPathCriteria fbAndAtsTargetPathCriteria;
    private PartitionCriterion partitionCriterion;
    private TargetBlockCriterion targetBlockCriterion;
    private PrevScoreCriterion prevScoreCriterion;
    private HelpOuchCriterion helpOuchCriterion;




}
