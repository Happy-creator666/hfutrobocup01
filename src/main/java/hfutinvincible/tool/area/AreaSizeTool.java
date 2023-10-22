package hfutinvincible.tool.area;

import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import adf.core.agent.info.WorldInfo;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 计算 Area 面积 (Size) 的工具
 * @author stardust
 */
@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class AreaSizeTool implements LifeCycle {
    @Autowired
    private WorldInfo worldInfo;

    private final Map<Area, Double> sizes; // Area -> Area 的面积
    private double totalSize;

    public AreaSizeTool() {
        this.sizes = new HashMap<>();
    }

    @Override
    public void precompute(PrecomputeData precomputeData) {
        init();
        // 写入预计算数据
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

