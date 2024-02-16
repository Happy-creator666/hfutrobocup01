package hfutinvincible.message.property;

import rescuecore2.worldmodel.EntityID;

import java.util.List;

/**
 * 多个EntityID的list集合
 */
public class PathIdsProperty {
    private List<EntityID> pathIds;

    public PathIdsProperty(List<EntityID> pathIds) {
        this.pathIds = pathIds;
    }

    public List<EntityID> getPathIds() {
        return pathIds;
    }
    @Override
    public String toString() {
        return getPathIds().toString();
    }
}
