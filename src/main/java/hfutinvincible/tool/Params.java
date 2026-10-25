package hfutinvincible.tool;

import hfutinvincible.FileWatcher;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.Properties;

public class Params {

    private static final String PARAMS_FILE_FOLDER_PATH = "config";
    private static final String PARAMS_FILE_NAME = "params.properties";
    private static final String PARAMS_FILE_PATH = PARAMS_FILE_FOLDER_PATH + File.separator + PARAMS_FILE_NAME;

    private static Properties PROPERTIES;

    private String prefix;

    private static FileWatcher fileWatcher;

    static  {
        try {
            FileReader reader = new FileReader(PARAMS_FILE_PATH);
            PROPERTIES = new Properties();
            PROPERTIES.load(reader);
            // 检测文件更改，并重新加载配置
            fileWatcher = new FileWatcher(PARAMS_FILE_FOLDER_PATH, PARAMS_FILE_NAME);
            fileWatcher.addEventListener(Params::reloadProperties);
        } catch (Exception ignore) {
        }
    }

    private static void reloadProperties() {
        try (FileReader reader = new FileReader(PARAMS_FILE_PATH)) {
            PROPERTIES.load(reader);
        } catch (Exception e) {
            // TODO 处理文件读取异常，比如记录日志
        }
    }


    public Params(@Nonnull String prefix) {
        this.prefix = prefix;
    }

    public synchronized void registerParamsChangedListener(Runnable cb) {
        fileWatcher.addEventListener(cb);
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

    public int getInt(String key, int fallback) {
        String value = PROPERTIES == null ? null : PROPERTIES.getProperty(prefix + "." + key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    public double getDouble(String key, double fallback) {
        String value = PROPERTIES == null ? null : PROPERTIES.getProperty(prefix + "." + key);
        return value == null ? fallback : Double.parseDouble(value);
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
