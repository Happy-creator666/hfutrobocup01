package hfutinvincible.tool.misc;

import org.apache.commons.lang3.StringUtils;
import org.jblas.DoubleMatrix;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public class MatrixUtils {

    // 标准化方法
    @Nonnull
    public static DoubleMatrix linearP1Normalization(@Nonnull DoubleMatrix columnVector) {
        double x = Math.max(columnVector.max(), 1e-12); // 防止除 0 错误
        return columnVector.div(x);
    }

    public static double isZeroVector(@Nonnull DoubleMatrix vector) {
        return vector.ne(0.0).sum() == 0
                ? 1.0
                : 0.0;
    }

    @Nonnull
    public static DoubleMatrix linearMinMaxNormalization(@Nonnull DoubleMatrix columnVector) {
        final double min = columnVector.min();
        final double max = columnVector.max();
        return max == min
                ? columnVector
                : columnVector.sub(min).div(max - min);
    }

    @Nonnull
    public static DoubleMatrix linearSumNormalization(@Nonnull DoubleMatrix columnVector) {
        double x = Math.max(columnVector.sum(), 1e-12); // 防止除 0 错误
        return columnVector.div(x);
    }

    // 指标换向
    @Nonnull
    public static DoubleMatrix maxPositivization(@Nonnull DoubleMatrix columnVector) {
        double maxVal = columnVector.max();
        return columnVector.rsub(maxVal);
    }

    // 异常值处理


    public static String matrixToString(
            @Nonnull DoubleMatrix dm,
            @Nonnull Function<Integer, String> rowNameProvider,
            @Nonnull Function<Integer, String> colNameProvider,
            @Nonnull Iterator<Integer> rowIndexGenerator
    ) {
        List<String> rowNames = IntStream
                .range(0, dm.rows)
                .mapToObj(rowNameProvider::apply)
                .toList();
        List<String> colNames = IntStream
                .range(0, dm.columns)
                .mapToObj(colNameProvider::apply)
                .toList();
        // 计算最长的 rowName 有几个字符
        int longestRowNameLength = 0;
        for (String rowName : rowNames)
            if (rowName.length() > longestRowNameLength)
                longestRowNameLength = rowName.length();
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        // 打印表头
        sb.append(StringUtils.repeat(' ', longestRowNameLength + 2));
        sb.append('|');
        List<Integer> desiredColNameLengths = new ArrayList<>(colNames.size());
        for (String colName : colNames) {
            int desiredColNameLength = Math.max(colName.length() + 2, 20);
            desiredColNameLengths.add(desiredColNameLength);
            sb.append(StringUtils.center(colName, desiredColNameLength));
            sb.append('|');
        }
        sb.append('\n');
        // 打印每一行
        while (rowIndexGenerator.hasNext()) {
            int i = rowIndexGenerator.next();
            sb.append(StringUtils.rightPad(rowNames.get(i), longestRowNameLength + 2));
            sb.append('|');
            for (int j = 0; j < dm.columns; j ++) {
                String value = StringUtils.truncate(String.valueOf(dm.get(i, j)), 7);
                sb.append(StringUtils.center(value, desiredColNameLengths.get(j)));
                sb.append('|');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}