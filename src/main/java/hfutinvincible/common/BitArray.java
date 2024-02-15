package hfutinvincible.common;

import adf.core.component.communication.util.BitOutputStream;
import adf.core.component.communication.util.BitStreamReader;

import javax.annotation.Nonnull;
import java.util.*;

public class BitArray {

    private static final boolean PATCH = true;

    private Deque<Boolean> data;

    private BitArray() {}

    /**
     * 返回一个 {@link BitArray} 的拷贝，其中的数据互相独立，彼此修改互不影响
     */
    public BitArray clone() {
        BitArray ret = new BitArray();
        ret.data = new ArrayDeque<>(this.data);
        return ret;
    }

    public static BitArray from(BitStreamReader bsr) {
        BitArray ret = new BitArray();
        ret.data = new ArrayDeque<>();
        try {
            while (true) {
                ret.addLast(bsr.getBits(1) == 1);
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {}
        return ret;
    }

    /**
     * 创建一个 BitArray，初始大小默认为 256
     */
    public static BitArray create() {
        return BitArray.create(256);
    }

    /**
     * 创建一个初始大小为 {@code initCap} 的 {@link BitArray}
     */
    public static BitArray create(int initCap) {
        BitArray ret = new BitArray();
        ret.data = new ArrayDeque<>(initCap);
        return ret;
    }

    /**
     * 创建一个初始大小为 {@code initCap}，初始值全为 {@code initVall} 的 {@link BitArray}
     */
    public static BitArray create(int initCap, boolean initVal) {
        BitArray ret = new BitArray();
        ret.data = new ArrayDeque<>(Collections.nCopies(initCap, initVal));
        return ret;
    }

    /**
     * 清空此 {@link BitArray}
     */
    public void clear() {
        this.data.clear();
    }

    /**
     * 返回此 {@link BitArray} 的长度
     */
    public int length() {
        return this.data.size();
    }

    /**
     * 头插一个 bit
     */
    public void addFirst(boolean bit) {
        this.data.addFirst(bit);
    }

    /**
     * 尾插一个 bit
     */
    public void addLast(boolean bit) {
        this.data.addLast(bit);
    }

    /**
     * 返回 [0, val] 的数编码为字节数组最多需要多少 bits
     */
    public static int upperBitLength(int val) {
        if (val < 0) {
            return 0;
        }
        if (val == 0)
            return 1;
        int length = 0;
        while (val > 0) {
            length++;
            val = val >> 1;
        }
        return length;
    }

    public List<Boolean> toBooleanList() {
        return new ArrayList<>(this.data);
    }

    /**
     * 将另一个 {@link BitArray} 拼接到此 {@link BitArray} 尾部
     */
    public void concat(@Nonnull BitArray another) {
        this.data.addAll(another.data);
    }

    /**
     * <p>取出 {@link BitArray} 头部的 N 个 bits，并按 int 解释</p>
     * <p><b>注意：这会消耗这个 {@link BitArray}！！！</b></p>
     */
    public int takeNAndDecode(int N) {
        int size = this.data.size();
        if (N >= size) {
            N = size;
        }
        int val = 0;
        for (int i = 0; i < N; i++) {
            val += val;
            if (Boolean.TRUE.equals(this.data.poll()))
                val += 1;
        }
        return val;
    }

    /**
     * 将此 {@link BitArray} 转换为 {@code byte[]}
     */
    public byte[] toByteArray() {
        BitArray clone = this.clone();
        int size = clone.length();
        int length = (size - 1) / 8 + 1;
        int patchLen = 8 * length - size;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) clone.takeNAndDecode(8);
        }
        int tmp = bytes[length - 1];
        for (int i = 0; i < patchLen; i++) {
            tmp = tmp << 1;
            tmp += (PATCH ? 1 : 0);
        }
        bytes[length - 1] = (byte) tmp;
        return bytes;
    }

    /**
     * 输出到 {@link BitOutputStream}
     */
    public BitOutputStream toBitOutputStream() {
        BitOutputStream stream = new BitOutputStream();
        stream.writeBytes(this.toByteArray());
        return stream;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (boolean b : this.data) {
            sb.append(b ? "1" : "0");
        }
        sb.append("]");
        return sb.toString();
    }
}
