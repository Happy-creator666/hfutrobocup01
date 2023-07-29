package hfutinvincible.mock;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.communication.CommunicationMessage;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class CommandPicker extends adf.core.component.centralized.CommandPicker {

    public CommandPicker(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public CommandPicker setAllocatorResult(Map<EntityID, EntityID> map) {
        return this;
    }

    @Override
    public CommandPicker calc() {
        return this;
    }

    @Override
    public Collection<CommunicationMessage> getResult() {
        return new ArrayList<>();
    }
}
