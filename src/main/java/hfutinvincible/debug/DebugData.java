package hfutinvincible.debug;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class DebugData {

    private Map<String, Object> data = new HashMap<>();

    public <T> DebugData set(String key, T data) {
        this.data.put(key, data);
        return this;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) this.data.get(key);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
