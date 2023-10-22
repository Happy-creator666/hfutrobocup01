package hfutinvincible.tool;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;

public class Params {

    private static final String PARAMS_FILE_NAME = "config" + File.separator + "params.properties";

    private static Properties PROPERTIES;

    private String prefix;

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

    public static Properties getProperties() {
        return PROPERTIES;
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