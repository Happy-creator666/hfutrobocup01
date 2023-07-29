package hfutinvincible.mock;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import rescuecore2.worldmodel.EntityID;

import java.util.HashMap;
import java.util.Map;

public class TargetAllocator extends adf.core.component.module.complex.TargetAllocator {

    public TargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public Map<EntityID, EntityID> getResult() {
        return new HashMap<>();
    }

    @Override
    public adf.core.component.module.complex.TargetAllocator calc() {
        return this;
    }
}
