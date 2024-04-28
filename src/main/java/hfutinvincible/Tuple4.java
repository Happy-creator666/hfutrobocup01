package hfutinvincible;

public class Tuple4<T0, T1, T2, T3> extends Tuple {

    public T0 e0;
    public T1 e1;
    public T2 e2;
    public T3 e3;

    private Tuple4(T0 e0, T1 e1, T2 e2, T3 e3) {
        this.e0 = e0;
        this.e1 = e1;
        this.e2 = e2;
        this.e3 = e3;
    }

    public static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> of(T1 e1, T2 e2, T3 e3, T4 e4) {
        return new Tuple4<>(e1, e2, e3, e4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(int i)  throws IndexOutOfBoundsException {
        if (i == 0)
            return (T) e0;
        if (i == 1)
            return (T) e1;
        if (i == 2)
            return (T) e2;
        if (i == 3)
            return (T) e3;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 3.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(int i, T newVal)  throws IndexOutOfBoundsException {
        if (i == 0)
            e0 = (T0) newVal;
        if (i == 1)
            e1 = (T1) newVal;
        if (i == 2)
            e2 = (T2) newVal;
        if (i == 3)
            e3 = (T3) newVal;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 3.");
    }

    @Override
    public int getElementNum() {
        return 4;
    }
}