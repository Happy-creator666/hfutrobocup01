package adf.core.launcher.connect;

import adf.core.component.tactics.TacticsAmbulanceTeam;
import adf.core.launcher.ConfigKey;
import adf.impl.DefaultLoader;
import org.junit.jupiter.api.Test;
import rescuecore2.components.Component;
import rescuecore2.components.ComponentConnectionException;
import rescuecore2.components.ComponentLauncher;
import rescuecore2.config.Config;
import rescuecore2.connection.Connection;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorAmbulanceTeamTest {
  private Config config(int count) {
    Config config = new Config();
    config.setIntValue(ConfigKey.KEY_AMBULANCE_TEAM_COUNT, count);
    return config;
  }

  @Test void createsExactlyOneTacticsInstancePerAttemptAndCountsOnlySuccesses() {
    CountingLoader loader = new CountingLoader();
    ComponentLauncher launcher = new ComponentLauncher(new Config()) {
      private int attempts;
      @Override public void connect(Component component) throws ComponentConnectionException {
        if (++attempts == 2) throw new ComponentConnectionException("no more ambulance slots");
      }
      @Override protected Connection makeConnection() { throw new AssertionError("no network in this test"); }
    };
    ConnectorAmbulanceTeam connector = new ConnectorAmbulanceTeam();
    connector.connect(launcher, config(3), loader);
    assertEquals(1, connector.getCountConnected());
    assertEquals(2, loader.calls);
  }

  @Test void disabledRoleDoesNotConstructAnyTactics() {
    CountingLoader loader = new CountingLoader();
    ConnectorAmbulanceTeam connector = new ConnectorAmbulanceTeam();
    connector.connect(null, config(0), loader);
    assertEquals(0, connector.getCountConnected());
    assertEquals(0, loader.calls);
  }

  @Test void interruptionIsPreservedAndPropagated() {
    ComponentLauncher launcher = new ComponentLauncher(new Config()) {
      @Override public void connect(Component component) throws InterruptedException {
        throw new InterruptedException("cancelled");
      }
      @Override protected Connection makeConnection() { throw new AssertionError("no network in this test"); }
    };
    try {
      assertThrows(IllegalStateException.class,
          () -> new ConnectorAmbulanceTeam().connect(launcher, config(1), new CountingLoader()));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private static final class CountingLoader extends DefaultLoader {
    private int calls;
    @Override public TacticsAmbulanceTeam getTacticsAmbulanceTeam() {
      calls++;
      return super.getTacticsAmbulanceTeam();
    }
  }
}
