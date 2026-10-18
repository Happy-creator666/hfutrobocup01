package adf.core;

import adf.core.launcher.AgentLauncher;
import adf.core.launcher.ConsoleOutput;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

  public static final String VERSION_CODE = "4.0.0";

  public static void main(String... args) {
    ConsoleOutput.version();
    Runtime runtime = Runtime.getRuntime();
    ConsoleOutput.info("Agent JVM heap (MiB): max=" + runtime.maxMemory() / (1024 * 1024)
        + ", committed=" + runtime.totalMemory() / (1024 * 1024)
        + ", used=" + (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        + "; readPrecomputeCache=" + System.getProperty("adf.precompute.read", "true"));

    // Process and check command line arguments
    List<String> launcherArguments = new ArrayList<>();
    launcherArguments.addAll(Arrays.asList(args));

    alias(launcherArguments, "-local", "-h", "localhost");
    alias(launcherArguments, "-all", "-t", "-1,-1,-1,-1,-1,-1");
    alias(launcherArguments, "-allp", "-t", "1,0,1,0,1,0");
    alias(launcherArguments, "-precompute", "-pre", "true");
    alias(launcherArguments, "-debug", "-d", "true");
    alias(launcherArguments, "-develop", "-dev", "true");

    if (launcherArguments.isEmpty()) {
      System.exit(0);
    }

    try {
      AgentLauncher connector = new AgentLauncher(
          launcherArguments.toArray(new String[0]));
      connector.start();
    } catch (ClassNotFoundException | InstantiationException
        | IllegalAccessException | NoSuchMethodException
        | InvocationTargetException e) {
      ConsoleOutput.out(ConsoleOutput.State.ERROR, "Loader is not found.");
      e.printStackTrace();
    }
  }


  // Expand Alias arguments
  private static void alias(List<String> args, String option,
      String... original) {
    if (args.contains(option)) {
      while (args.contains(option)) {
        args.remove(option);
      }
      args.addAll(Arrays.asList(original));
    }
  }
}
