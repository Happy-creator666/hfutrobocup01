package hfutinvincible;

public class Tuple3<T0, T1, T2> extends Tuple {
    public T0 e0;
    public T1 e1;
    public T2 e2;

    private Tuple3(T0 e0, T1 e1, T2 e2) {
        this.e0 = e0;
        this.e1 = e1;
        this.e2 = e2;
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 e1, T2 e2, T3 e3) {
        return new Tuple3<>(e1, e2, e3);
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
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 2.");
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
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 2.");
    }

    @Override
    public int getElementNum() {
        return 3;
    }
}