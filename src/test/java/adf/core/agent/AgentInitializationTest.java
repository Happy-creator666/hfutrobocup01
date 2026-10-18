package adf.core.agent;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.config.ModuleConfig;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.platoon.PlatoonAmbulance;
import adf.impl.tactics.DefaultTacticsAmbulanceTeam;
import org.junit.jupiter.api.Test;
import rescuecore2.components.ComponentConnectionException;
import rescuecore2.config.Config;
import rescuecore2.connection.Connection;
import rescuecore2.connection.ConnectionListener;
import rescuecore2.messages.Message;
import rescuecore2.messages.control.AKConnect;
import rescuecore2.messages.control.AKAcknowledge;
import rescuecore2.messages.control.KAConnectOK;
import rescuecore2.registry.Registry;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AgentInitializationTest {
  @Test void failedTacticsInitializationReleasesHandshakeAndCannotRunDecisions() {
    TestAmbulance agent = new TestAmbulance();
    FakeConnection connection = new FakeConnection();
    Config config = new Config();
    config.setIntValue("kernel.agents.ignoreuntil", 0);
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      ComponentConnectionException failure = assertThrows(ComponentConnectionException.class,
          () -> agent.connect(connection, () -> 1, config));
      assertEquals("deliberate initialization failure", failure.getCause().getMessage());
      assertTrue(connection.acknowledged, "handshake must release before reporting initialization failure");
      assertFalse(connection.isAlive(), "failed agent transport must be shut down");
      assertDoesNotThrow(agent::deliverSenseWithoutData, "failed agent must not enter its decision loop");
    });
  }

  private static final class TestAmbulance extends PlatoonAmbulance {
    private TestAmbulance() {
      super(new DefaultTacticsAmbulanceTeam() {
        @Override public void initialize(AgentInfo ai, WorldInfo wi, ScenarioInfo si,
                                        ModuleManager mm, MessageManager messages, DevelopData dd) {
          throw new IllegalStateException("deliberate initialization failure");
        }
      }, "test", false, false, new ModuleConfig("config/module.cfg", List.of()),
          new DevelopData(false, "config/develop.json", List.of()));
    }
    @Override protected boolean shouldIndex() { return false; }
    private void deliverSenseWithoutData() { processSense(null); }
  }

  /** Deliver the connect reply asynchronously, as the real connection does. */
  private static final class FakeConnection implements Connection {
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean alive = true;
    private volatile boolean acknowledged;
    @Override public void sendMessage(Message message) {
      if (message instanceof AKConnect request) {
        Road road = new Road(new EntityID(10));
        road.setX(0);
        road.setY(0);
        road.setEdges(List.of(new Edge(0, 0, 10, 0), new Edge(10, 0, 0, 10), new Edge(0, 10, 0, 0)));
        AmbulanceTeam human = new AmbulanceTeam(new EntityID(20));
        human.setPosition(road.getID());
        KAConnectOK reply = new KAConnectOK(request.getRequestID(), human.getID(), List.of(road, human), new Config());
        Thread receiver = new Thread(() -> listeners.forEach(listener -> listener.messageReceived(this, reply)));
        receiver.setDaemon(true);
        receiver.start();
      } else if (message instanceof AKAcknowledge) {
        acknowledged = true;
      }
    }
    @Override public void sendMessages(Collection<? extends Message> messages) { messages.forEach(this::sendMessage); }
    @Override public void addConnectionListener(ConnectionListener listener) { listeners.add(listener); }
    @Override public void removeConnectionListener(ConnectionListener listener) { listeners.remove(listener); }
    @Override public void startup() { alive = true; }
    @Override public boolean isAlive() { return alive; }
    @Override public void shutdown() { alive = false; }
    @Override public void setLogBytes(boolean enabled) {}
    @Override public void setName(String name) {}
    @Override public String getName() { return "test connection"; }
    @Override public void setRegistry(Registry registry) {}
    @Override public Registry getRegistry() { return Registry.SYSTEM_REGISTRY; }
  }
}
