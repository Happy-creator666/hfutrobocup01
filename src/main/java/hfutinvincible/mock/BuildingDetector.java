package hfutinvincible.mock;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import rescuecore2.worldmodel.EntityID;

public class BuildingDetector extends adf.core.component.module.complex.BuildingDetector {

    public BuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public EntityID getTarget() {
        return null;
    }

    @Override
    public adf.core.component.module.complex.BuildingDetector calc() {
        return this;
    }
}
