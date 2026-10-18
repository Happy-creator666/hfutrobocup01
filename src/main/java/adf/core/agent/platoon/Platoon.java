package adf.core.agent.platoon;

import adf.core.agent.Agent;
import adf.core.agent.action.Action;
import adf.core.agent.config.ModuleConfig;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.tactics.Tactics;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.connection.ConnectionException;

public abstract class Platoon<E extends StandardEntity>extends Agent<E> {

  private Tactics rootTactics;

  Platoon(Tactics tactics, String teamName, boolean isPrecompute, String dataStorageName, boolean isDebugMode, ModuleConfig moduleConfig, DevelopData developData) {
    super(teamName, isPrecompute, dataStorageName, isDebugMode, moduleConfig,
        developData);
    this.rootTactics = tactics;
  }


  @Override
  protected void postConnect() {
    try {
      initializeAfterConnect();
      markInitialized();
    } catch (RuntimeException e) {
      markInitializationFailed(e);
    }
  }

  private void initializeAfterConnect() {
    super.postConnect();

    this.agentInfo = new AgentInfo(this, this.model);
    this.moduleManager = new ModuleManager(this.agentInfo, this.worldInfo,
        this.scenarioInfo, this.moduleConfig, this.developData);
    this.messageManager.setChannelSubscriber(moduleManager.getChannelSubscriber(
        "MessageManager.PlatoonChannelSubscriber",
        "adf.component.communication.ChannelSubscriber"));
    this.messageManager.setMessageCoordinator(moduleManager
        .getMessageCoordinator("MessageManager.PlatoonMessageCoordinator",
            "adf.core.agent.communication.standard.bundle.StandardMessageCoordinator"));

    this.rootTactics.initialize(this.agentInfo, this.worldInfo,
        this.scenarioInfo, this.moduleManager, this.messageManager,
        this.developData);

    switch (this.scenarioInfo.getMode()) {
      case NON_PRECOMPUTE:
        this.rootTactics.preparate(this.agentInfo, this.worldInfo,
            this.scenarioInfo, this.moduleManager, this.developData);
        this.worldInfo.registerRollbackListener();
        break;
      case PRECOMPUTATION_PHASE:
        this.precomputeData.generateAndWrite(this.worldInfo, () -> this.rootTactics.precompute(this.agentInfo, this.worldInfo,
            this.scenarioInfo, this.moduleManager, this.precomputeData,
            this.developData));
        break;
      case PRECOMPUTED:
        this.rootTactics.resume(this.agentInfo, this.worldInfo,
            this.scenarioInfo, this.moduleManager, this.precomputeData,
            this.developData);
        this.worldInfo.registerRollbackListener();
        break;
      default:
    }
  }


  protected void think() {
    Action action = this.rootTactics.think(this.agentInfo, this.worldInfo,
        this.scenarioInfo, this.moduleManager, this.messageManager,
        this.developData);
    if (action != null) {
      this.agentInfo.setExecutedAction(this.agentInfo.getTime(), action);
      try {
        connection.sendMessage(action.getCommand(this.getID(), this.agentInfo.getTime()));
        markActionSubmitted();
      } catch (ConnectionException e) {
        throw new IllegalStateException("Cannot submit action for " + this.getID(), e);
      }
    }
  }
}
