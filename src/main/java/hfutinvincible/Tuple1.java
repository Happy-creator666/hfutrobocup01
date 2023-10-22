package hfutinvincible;

public class Tuple1<T0> extends Tuple {

    public T0 e0;

    private Tuple1(T0 e) {
        this.e0 = e;
    }

    public static <T> Tuple1<T> of(T e) {
        return new Tuple1<>(e);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T0 get(int i)  throws IndexOutOfBoundsException {
        if (i == 0)
            return e0;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 0.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(int i, T newVal)  throws IndexOutOfBoundsException {
        if (i == 0)
            e0 = (T0) newVal;
        throw new IndexOutOfBoundsException("Tuple index out of bound. " + i + " of 0.");
    }

    @Override
    public int getElementNum() {
        return 1;
    }
}