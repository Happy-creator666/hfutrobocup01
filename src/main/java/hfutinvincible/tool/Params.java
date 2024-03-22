package hfutinvincible.tool;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class Params {
    private static final String PARAMS_FILE_NAME = "config" + File.separator + "params.properties";

    private static Properties PROPERTIES;

    private String prefix;

    private List<Consumer<Properties>> changeListeners = new ArrayList<>();


    public Params(@Nonnull String prefix) {
        if (PROPERTIES == null) {
            try {
                FileReader reader = new FileReader(PARAMS_FILE_NAME);
                PROPERTIES = new Properties();
                PROPERTIES.load(reader);
            } catch (Exception ignore) {
            }
        }
        this.prefix = prefix;
    }

    public void registerChangeListener(Consumer<Properties> cb) {
        // 添加回调到列表中
        this.changeListeners.add(cb);
        // 第一次添加回调时启动监听
        if (this.changeListeners.size() == 1) {
            startWatching();
        }
    }

    private void startWatching() {
        Thread watcherThread = new Thread(() -> {
            System.out.println("start watching");
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = Paths.get(PARAMS_FILE_NAME).getParent();
                path.register(watchService, ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == ENTRY_MODIFY) {
                            Path changed = (Path) event.context();
                            if (changed.endsWith(PARAMS_FILE_NAME)) {
                                // 重新加载配置文件
                                reloadProperties();
                                System.out.println("changed!!");
                                // 遍历回调列表并触发每个回调
                                for (Consumer<Properties> listener : changeListeners) {
                                    listener.accept(PROPERTIES);
                                }
                            }
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                // 处理异常，比如记录日志
            }
        });

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void reloadProperties() {
        try (FileReader reader = new FileReader(PARAMS_FILE_NAME)) {
            PROPERTIES.load(reader);
        } catch (Exception e) {
            // 处理文件读取异常，比如记录日志
        }
    }

    public String getString(@Nonnull String propertKey) {
        return PROPERTIES.getProperty(prefix + "." + propertKey);
    }

    public void setString(@Nonnull String propertKey, @Nonnull String value) {
        PROPERTIES.setProperty(prefix + "." + propertKey, value);
    }

    public double getDouble(@Nonnull String propertKey) {
        return Double.parseDouble(PROPERTIES.getProperty(prefix + "." + propertKey));
    }

    public void setDouble(@Nonnull String propertKey, double value) {
        PROPERTIES.setProperty(prefix + "." + propertKey, String.valueOf(value));
    }

    public int getInt(@Nonnull String propertKey) {
        return Integer.parseInt(PROPERTIES.getProperty(prefix + "." + propertKey));
    }

    public void setInt(@Nonnull String propertKey, int value) {
        PROPERTIES.setProperty(prefix + "." + propertKey, String.valueOf(value));
    }

    public boolean getBoolean(@Nonnull String propertKey) {
        String value = PROPERTIES.getProperty(prefix + "." + propertKey);
        return value.equals("true") || value.equals("1");
    }

    public void setBoolean(@Nonnull String propertKey, boolean value) {
        PROPERTIES.setProperty(prefix + "." + propertKey, value ? "true" : "false");
    }
}
