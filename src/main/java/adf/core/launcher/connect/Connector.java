package adf.core.launcher.connect;

import adf.core.component.AbstractLoader;
import adf.core.launcher.ConsoleOutput;
import rescuecore2.components.ComponentLauncher;
import rescuecore2.config.Config;

public abstract class Connector {

  int connected = 0;

  public abstract void connect(ComponentLauncher launcher, Config config,
      AbstractLoader loader);


  public int getCountConnected() {
    return connected;
  }

  protected void reportConnectionFailure(String type, int requested, Exception failure) {
    ConsoleOutput.warn(type + " connection stopped (requested=" + requested + ", connected=" + connected
        + "): " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    failure.printStackTrace(System.err);
    if (requested > 0 && connected < requested) {
      ConsoleOutput.error(type + " shortfall: requested=" + requested + ", connected=" + connected);
    }
    if (failure instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(type + " connection interrupted", failure);
    }
  }
}
