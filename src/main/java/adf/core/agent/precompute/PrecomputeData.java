package adf.core.agent.precompute;

import adf.core.agent.info.WorldInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PrecomputeData {

  public static final String DEFAULT_FILE_NAME = "data.bin";
  public static final File PRECOMP_DATA_DIR = new File("precomp_data");
  public static final String READ_CACHE_PROPERTY = "adf.precompute.read";
  public static final int FORMAT_VERSION = 2;

  private String fileName;

  private PreData data;

  public PrecomputeData() {
    this(DEFAULT_FILE_NAME);
  }


  public PrecomputeData(String name) {
    this(name, isCacheReadEnabled());
  }

  public PrecomputeData(String name, boolean readExisting) {
    this.fileName = name;
    this.data = readExisting ? read(name) : null;
    if (!hasValidStructure()) this.data = new PreData();
  }

  public static boolean isCacheReadEnabled() {
    String value = System.getProperty(READ_CACHE_PROPERTY, "true");
    if (!value.equals("true") && !value.equals("false")) {
      throw new IllegalArgumentException(READ_CACHE_PROPERTY + " must be true or false");
    }
    return Boolean.parseBoolean(value);
  }


  private PrecomputeData(String name, PreData precomputeDatas) {
    this.fileName = name;
    this.data = precomputeDatas;
  }


  public static void removeData(String name) {
    if (!PRECOMP_DATA_DIR.exists()) {
      return;
    }

    File file = new File(PRECOMP_DATA_DIR, name);
    if (!file.exists()) {
      return;
    }

    file.delete();
  }


  public static void removeData() {
    removeData(DEFAULT_FILE_NAME);
  }


  public PrecomputeData copy() {
    return new PrecomputeData(this.fileName, this.data.copy());
  }


  private PreData read(String name) {
    try {
      File readFile = cacheFile(name);
      if (!readFile.exists()) {
        return null;
      }
      ObjectMapper om = new ObjectMapper(new MessagePackFactory());
      try (InputStream input = new BufferedInputStream(new FileInputStream(readFile))) {
        return om.readValue(input, PreData.class);
      }
    } catch (IOException | RuntimeException e) {
      System.err.println("Cannot read precompute cache " + name + ": " + e.getMessage());
      return null;
    }
  }


  public boolean write() {
    if (!hasValidStructure() || !this.data.isReady) {
      System.err.println("Refusing to write incomplete precompute cache: " + this.fileName);
      return false;
    }
    Path temporary = null;
    try {
      Path target = cacheFile(this.fileName).toPath().toAbsolutePath();
      Files.createDirectories(target.getParent());
      temporary = Files.createTempFile(target.getParent(), "precompute-", ".tmp");
      ObjectMapper om = new ObjectMapper(new MessagePackFactory());
      try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
        om.writeValue(output, this.data);
      }
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException e) {
          System.err.println("Cannot remove temporary precompute file: " + temporary);
        }
      }
    }
  }

  private static File cacheFile(String name) {
    Path path = Path.of(name);
    return path.isAbsolute() ? path.toFile() : new File(PRECOMP_DATA_DIR, name);
  }

  /** Publish only after all callbacks succeed; keep an existing file on computation failure. */
  public void generateAndWrite(WorldInfo worldInfo, Runnable computation) {
    this.data = new PreData();
    try {
      computation.run();
      setReady(true, worldInfo);
      if (!write()) throw new IllegalStateException("Cannot publish precompute cache: " + fileName);
    } catch (RuntimeException | Error failure) {
      setReady(false, worldInfo);
      throw failure;
    }
  }


  public Integer setInteger(String name, int value) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.intValues.put(callClassName + ":" + name, value);
  }


  public Double setDouble(String name, double value) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.doubleValues.put(callClassName + ":" + name, value);
  }


  public Boolean setBoolean(String name, boolean value) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.boolValues.put(callClassName + ":" + name, value);
  }


  public String setString(String name, String value) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.stringValues.put(callClassName + ":" + name, value);
  }


  public EntityID setEntityID(String name, EntityID value) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    Integer id = this.data.idValues.put(callClassName + ":" + name,
        value.getValue());
    return id == null ? null : new EntityID(id);
  }


  public List<Integer> setIntegerList(String name, List<Integer> list) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.intLists.put(callClassName + ":" + name, list);
  }


  public List<Double> setDoubleList(String name, List<Double> list) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.doubleLists.put(callClassName + ":" + name, list);
  }


  public List<String> setStringList(String name, List<String> list) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.stringLists.put(callClassName + ":" + name, list);
  }


  public List<EntityID> setEntityIDList(String name, List<EntityID> list) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    List<Integer> cvtList = new ArrayList<>();
    for (EntityID id : list) {
      cvtList.add(id.getValue());
    }

    cvtList = this.data.idLists.put(callClassName + ":" + name, cvtList);
    return cvtList == null ? null
        : cvtList.stream().map(EntityID::new).collect(Collectors.toList());
  }


  public List<Boolean> setBooleanList(String name, List<Boolean> list) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.boolLists.put(callClassName + ":" + name, list);
  }


  public boolean setReady(boolean isReady, WorldInfo worldInfo) {
    if (isReady && !hasValidPayload()) {
      throw new IllegalStateException("Precompute data contains missing or null values");
    }
    this.data.isReady = isReady;
    this.data.formatVersion = FORMAT_VERSION;
    this.data.readyID = isReady ? makeReadyID(worldInfo) : "";
    return isReady;
  }

  public void markModuleComplete(String moduleName) {
    if (!this.data.completedModules.contains(moduleName)) this.data.completedModules.add(moduleName);
  }

  public void requireCompletedModule(String moduleName) {
    if (!this.data.completedModules.contains(moduleName)) {
      throw new IllegalStateException("Incomplete precompute cache: module not completed: " + moduleName
          + ". Regenerate the cache or disable cache reads with -Dadf.precompute.read=false");
    }
  }


  public Integer getInteger(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.intValues.get(callClassName + ":" + name);
  }


  public Double getDouble(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.doubleValues.get(callClassName + ":" + name);
  }


  public Boolean getBoolean(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.boolValues.get(callClassName + ":" + name);
  }


  public String getString(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.stringValues.get(callClassName + ":" + name);
  }


  public EntityID getEntityID(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    Integer id = this.data.idValues.get(callClassName + ":" + name);
    return id == null ? null : new EntityID(id);
  }


  public List<Integer> getIntegerList(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.intLists.get(callClassName + ":" + name);
  }


  public List<Double> getDoubleList(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.doubleLists.get(callClassName + ":" + name);
  }


  public List<String> getStringList(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.stringLists.get(callClassName + ":" + name);
  }


  public List<EntityID> getEntityIDList(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    List<Integer> cvtList = this.data.idLists.get(callClassName + ":" + name);
    return cvtList == null ? null
        : cvtList.stream().map(EntityID::new).collect(Collectors.toList());
  }


  public List<Boolean> getBooleanList(String name) {
    StackTraceElement[] stackTraceElements = Thread.currentThread()
        .getStackTrace();
    if (stackTraceElements == null || stackTraceElements.length == 0) {
      return null;
    }

    String callClassName = stackTraceElements[2].getClassName();
    return this.data.boolLists.get(callClassName + ":" + name);
  }


  public boolean isReady(WorldInfo worldInfo) {
    return hasValidStructure() && this.data.isReady
        && this.data.readyID.equals(makeReadyID(worldInfo));
  }

  private boolean hasValidStructure() {
    return data != null && data.formatVersion == FORMAT_VERSION
        && data.readyID != null && hasValidPayload();
  }

  private boolean hasValidPayload() {
    return data != null && data.completedModules != null && !data.completedModules.contains(null)
        && validMap(data.intValues) && validMap(data.doubleValues) && validMap(data.boolValues)
        && validMap(data.stringValues) && validMap(data.idValues)
        && validLists(data.intLists) && validLists(data.doubleLists) && validLists(data.boolLists)
        && validLists(data.stringLists) && validLists(data.idLists);
  }

  private static boolean validMap(Map<?, ?> values) {
    return values != null && !values.containsKey(null) && !values.containsValue(null);
  }

  private static boolean validLists(Map<?, ? extends List<?>> values) {
    return validMap(values) && values.values().stream()
        .noneMatch(list -> list.stream().anyMatch(java.util.Objects::isNull));
  }

  private String makeReadyID(WorldInfo worldInfo) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<StandardEntity> entities = new ArrayList<>(worldInfo.getAllEntities());
      entities.sort(Comparator.comparingInt(entity -> entity.getID().getValue()));
      for (StandardEntity entity : entities) {
        hashInt(digest, entity.getID().getValue());
        hashInt(digest, entity.getURN());
        if (entity instanceof Area area) {
          hashInt(digest, area.isXDefined() ? area.getX() : Integer.MIN_VALUE);
          hashInt(digest, area.isYDefined() ? area.getY() : Integer.MIN_VALUE);
          int[] apexes = area.getApexList();
          hashInt(digest, apexes.length);
          for (int coordinate : apexes) hashInt(digest, coordinate);
          int[] neighbours = area.getNeighbours().stream().mapToInt(EntityID::getValue).sorted().toArray();
          hashInt(digest, neighbours.length);
          for (int neighbour : neighbours) hashInt(digest, neighbour);
        } else if (entity instanceof Human human && (entity.getStandardURN() == StandardEntityURN.AMBULANCE_TEAM
            || entity.getStandardURN() == StandardEntityURN.FIRE_BRIGADE
            || entity.getStandardURN() == StandardEntityURN.POLICE_FORCE)) {
          hashInt(digest, human.isPositionDefined() ? human.getPosition().getValue() : Integer.MIN_VALUE);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static void hashInt(MessageDigest digest, int value) {
    digest.update((byte) (value >>> 24));
    digest.update((byte) (value >>> 16));
    digest.update((byte) (value >>> 8));
    digest.update((byte) value);
  }
}
