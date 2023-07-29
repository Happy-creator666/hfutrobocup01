package hfutinvincible.mock;


import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.communication.CommunicationMessage;

public class CommandExecutor extends adf.core.component.centralized.CommandExecutor<CommunicationMessage> {

    public CommandExecutor(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public CommandExecutor setCommand(CommunicationMessage communicationMessage) {
        return this;
    }

    @Override
    public CommandExecutor calc() {
        return this;
    }
}
