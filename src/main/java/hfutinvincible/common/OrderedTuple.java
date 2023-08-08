package hfutinvincible.common;

public class OrderedTuple {

    Object[] objects;

    private OrderedTuple() {}

    public static OrderedTuple of(Object... objects) {
        OrderedTuple ret = new OrderedTuple();
        ret.objects = objects;
        return ret;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(int i) {
        return (T) objects[i];
    }

    public void set(int i, Object newValue) {
        objects[i] = newValue;
    }
}
