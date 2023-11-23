package hfutinvincible.tool.area;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

@HfutTool
@AgentType({FireBrigade.class, PoliceForce.class, AmbulanceTeam.class})
public class AreaSizeTool implements LifeCycle {
    private final Map<Area, Double> sizes; // Area -> Area 的面积
    private double totalSize;

    @Autowired AgentInfo agentInfo;
    @Autowired WorldInfo worldInfo;
    @Autowired ScenarioInfo scenarioInfo;
    @Autowired ModuleManager moduleManager;
    @Autowired DevelopData developData;



    public AreaSizeTool()
    {
        this.sizes=new HashMap<>();
    }


    @Override
    public void precompute(PrecomputeData precomputeData) {
        init();

        //写入预计算数据
        final String className = this.getClass().getCanonicalName();
        List<Map.Entry<Area, Double>> sizesEntryList = new ArrayList<>(this.sizes.entrySet());
        List<EntityID> areaIdList = sizesEntryList.stream().map(o -> o.getKey().getID()).collect(Collectors.toList());
        List<Double> areaSizeList = sizesEntryList.stream().map(Map.Entry::getValue).collect(Collectors.toList());
        precomputeData.setEntityIDList(className + ".areaidlist", areaIdList);
        precomputeData.setDoubleList(className + ".areasizelist", areaSizeList);
        precomputeData.setDouble(className + ".totalsize", this.totalSize);
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        // 读取预计算数据
        final String className = this.getClass().getCanonicalName();
        List<EntityID> areaIdList = precomputeData.getEntityIDList(className + ".areaidlist");
        List<Double> areaSizeList = precomputeData.getDoubleList(className + ".areasizelist");
        Iterator<EntityID> iter1 = areaIdList.iterator();
        Iterator<Double> iter2 = areaSizeList.iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            Area area = (Area) worldInfo.getEntity(iter1.next());
            this.sizes.put(area, iter2.next());
        }
        this.totalSize = precomputeData.getDouble(className + ".totalsize");
    }

    @Override
    public void preparate() {
        init();
    }

    public double getSize(Area area) {
        return sizes.get(area);
    }

    public double getTotalSize() {
        return totalSize;
    }

    private void init() {
        sizes.clear();
        totalSize = 0.0;
        for (StandardEntity entity: worldInfo.getAllEntities()) {
            if (entity instanceof Area area) {
                double size = HFUTGeometryTool.computeArea(HFUTGeometryTool.vertexArrayToPoints(area.getApexList()));
                sizes.put(area, size);
                totalSize += size;
            }
        }
    }

}