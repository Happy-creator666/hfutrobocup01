package hfutinvincible.tool.edge;

import org.locationtech.jts.geom.Geometry;
import rescuecore2.worldmodel.EntityID;

public class EdgePairInfo {

    /**
     * -1 - 我不知道 <br>
     * 0 - 不能过 <br>
     * 1 - 可以过
     * 2 - 中心线堵住
     */
    private int passability;
    private int lastUpdateTime;
    /** 两个 edge 中间夹住的 Area 的 EntityID */
    private EntityID areaId;
    /** 智能体中心可以自由活动的区域 */
    private Geometry cfreePolygon;

    public EdgePairInfo(int passability, int lastUpdateTime, EntityID areaId, Geometry cfreePolygon) {
        this.passability = passability;
        this.lastUpdateTime = lastUpdateTime;
        this.areaId = areaId;
        this.cfreePolygon = cfreePolygon;
    }

    public int getPassability() {
        return passability;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public EntityID getAreaId() {
        return areaId;
    }

    public Geometry getCfreePolygon() {
        return cfreePolygon;
    }

    public void setPassability(int passability) {
        this.passability = passability;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setAreaId(EntityID areaId) {
        this.areaId = areaId;
    }

    public void setCfreePolygon(Geometry cfreePolygon) {
        this.cfreePolygon = cfreePolygon;
    }
}