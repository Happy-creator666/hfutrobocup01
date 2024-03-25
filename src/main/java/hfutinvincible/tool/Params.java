package hfutinvincible.tool;

import hfutinvincible.FileWatcher;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

public class Params {

    private static final String PARAMS_FILE_FOLDER_PATH = "config";
    private static final String PARAMS_FILE_NAME = "params.properties";
    private static final String PARAMS_FILE_PATH = PARAMS_FILE_FOLDER_PATH + File.separator + PARAMS_FILE_NAME;

    private static Properties PROPERTIES;

    private String prefix;

    private static FileWatcher fileWatcher;


    public Params(@Nonnull String prefix) {
        if (PROPERTIES == null) {
            try {
                FileReader reader = new FileReader(PARAMS_FILE_PATH);
                PROPERTIES = new Properties();
                PROPERTIES.load(reader);
                fileWatcher = new FileWatcher(PARAMS_FILE_FOLDER_PATH, PARAMS_FILE_NAME);
                fileWatcher.addEventListener(() -> {
                    System.out.println("reload config file");
                    reloadProperties();
                });
            } catch (Exception ignore) {
            }
        }
        this.prefix = prefix;
    }

    public void registerParamsChangedListener(Runnable cb) {
        fileWatcher.addEventListener(cb);
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
