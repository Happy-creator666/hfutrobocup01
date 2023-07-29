package hfutinvincible.mock;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import rescuecore2.worldmodel.EntityID;

public class ExtAction extends adf.core.component.extaction.ExtAction {

    public ExtAction(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public adf.core.component.extaction.ExtAction setTarget(EntityID entityID) {
        return this;
    }

    @Override
    public adf.core.component.extaction.ExtAction calc() {
        return this;
    }
}