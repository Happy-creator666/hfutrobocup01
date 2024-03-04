package hfutinvincible;

import hfutinvincible.debug.TestLogger;
import adf.core.component.communication.util.BitOutputStream;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 比特数组，用于信息的编码与解码
 */
public class BitArray implements Iterable<Boolean> {

    private static final Logger logger = TestLogger.getLogger(BitArray.class.getName());

    private static final boolean PATCH = true;

    /**
     * 每个 Boolean 表示一个 bit. <br>
     * 注意：LinkedList 的 size() 方法是 O(1) 的.
     */
    private LinkedList<Boolean> data;

    public BitArray() {
        this.data = new LinkedList<>();
    }

    public BitArray(List<Boolean> data) {
        this.data = new LinkedList<>(data);
    }

    public void clear() {
        this.data.clear();
    }

    /**
     * 返回 BitArray 存了多少个 bit
     */
    public int length() {
        return this.data.size();
    }

    public void appendFirst(boolean bit) {
        this.data.addFirst(bit);
    }

    public void append(boolean bit) {
        this.data.add(bit);
    }

    /**
     * 返回 BitArray 中的存内容
     */
    public List<Boolean> getAllBits() {
        return this.data;
    }

    public BitArray append(BitArray other) {
        this.data.addAll(other.getAllBits());
        return this;
    }

    /**
     * 将 val 编码为长为 nbits 的 BitArray
     */
    public static BitArray encode(int val, int nbits) {
        if (val < 0)
            logger.error("The value being encoded " + val + " is negative.");
        BitArray array = new BitArray();
        for (int i = 0; i < nbits; i++) {
            array.appendFirst((val & 0x1) != 0);
            val = val >> 1;
        }
        return array;
    }

    /**
     * 将 BitArray 按 int 解释
     */
    public int decode() {
        int val = 0;
        for (boolean bit : this.data) {
            val += val;
            if (bit) val += 1;
        }
        return val;
    }

    /**
     * 取出 BitArray 头部的 N 个 bits
     */
    public BitArray takeN(int N) {
        int size = this.data.size();
        if (N >= size) {
            logger.error("BitArray has no enough length to truncation, only " + size + " of " + N + ".");
            N = size;
        }
        List<Boolean> list = new LinkedList<Boolean>();
        for (int i = 0; i < N; i++) {
            list.add(this.data.poll());
        }
        return new BitArray(list);
    }

    /**
     * 取出 BitArray 头部的 N 个 bits，并按 int 解释
     */
    public int takeNAndDecode(int N) {
        int size = this.data.size();
        if (N >= size) {
            logger.error("BitArray has no enough length to truncate, only " + this.data.size() + " of " + N + ".");
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

    public static BitArray fromByteArray(byte[] bytes) {
        BitArray array = new BitArray();
        for (byte by : bytes) {
            int val = by;
            for (int i = 0; i < 8; i++) {
                array.append((val & 0x80) != 0);
                val = val << 1;
            }
        }
        return array;
    }

    public byte[] toByteArray() {
        int size = this.data.size();
        int length = (size - 1) / 8 + 1;
        int patchLen = 8 * length - size;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) takeNAndDecode(8);
        }
        int tmp = bytes[length - 1];
        for (int i = 0; i < patchLen; i++) {
            tmp = tmp << 1;
            tmp += (PATCH ? 1 : 0);
        }
        bytes[length - 1] = (byte) tmp;
        return bytes;
    }

    public boolean isPatch() {
        if (this.data.size() >= 8)
            return false;
        for (boolean b : this.data) {
            if (b != PATCH)
                return false;
        }
        return true;
    }

    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    /**
     * 返回 [0, val] 的数编码为字节数组最多需要多少 bits
     */
    public static int upperBitLength(int val) {
        if (val < 0) {
            logger.error("The value being encoded " + val + " is negative.");
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

    /**
     * 返回 bitLength 个比特可以编码的最大数
     */
    public static int upperValue(int bitLength) {
        if (bitLength == 0)
            return 0;
        else {
            int b = 1;
            for (int i = 0; i < bitLength; i++)
                b <<= 1;
            return b - 1;
        }
    }

    /**
     * 创建长为 bitLength 的全为 1 的 BitArray
     */
    public static BitArray ones(int bitLength) {
        return new BitArray(Collections.nCopies(bitLength, true));
    }

    /**
     * 创建长为 bitLength 的全为 0 的 BitArray
     */
    public static BitArray zeroes(int bitLength) {
        return new BitArray(Collections.nCopies(bitLength, false));
    }

    /**
     * 输出到 BitOutputStream
     */
    public BitOutputStream toBitOutputStream() {
        BitOutputStream stream = new BitOutputStream();
        stream.writeBytes(this.toByteArray());
        return stream;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BitArray (raw data: ");
        for (Boolean b : this.data) {
            builder.append(b ? "1" : "0");
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public Iterator<Boolean> iterator() {
        return this.data.iterator();
    }

    public int get(int index) {
        if (index >= 0 && index < this.data.size())
            return this.data.get(index) ? 1 : 0;
        else {
            logger.error("Index " + index + " is illegal for BitArray " + this + ".");
            return PATCH ? 1 : 0;
        }
    }
}