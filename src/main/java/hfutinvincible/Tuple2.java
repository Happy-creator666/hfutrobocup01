package hfutinvincible;

public class Tuple2<T0, T1> extends Tuple {

    public T0 e0;
    public T1 e1;

    private Tuple2(T0 e0, T1 e1) {
        this.e0 = e0;
        this.e1 = e1;
    }

    public static <T1, T2> Tuple2<T1, T2> of(T1 e1, T2 e2) {
        return new Tuple2<>(e1, e2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(int i) throws IndexOutOfBoundsException {
        if (i == 0)
            return (T) e0;
        if (i == 1)
            return (T) e1;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 1.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(int i, T newVal)  throws IndexOutOfBoundsException {
        if (i == 0)
            e0 = (T0) newVal;
        if (i == 1)
            e1 = (T1) newVal;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 1.");
    }

    @Override
    public int getElementNum() {
        return 2;
    }
}
