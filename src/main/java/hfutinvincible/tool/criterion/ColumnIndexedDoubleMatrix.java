package hfutinvincible.tool.criterion;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.jblas.DoubleMatrix;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ColumnIndexedDoubleMatrix {

    private int nextFreeColumn = 0;
    private final BiMap<String, Integer> columnIndexMap;
    private DoubleMatrix values;

    public ColumnIndexedDoubleMatrix(int numRows) {
        this.columnIndexMap = HashBiMap.create();
        this.values = DoubleMatrix.zeros(numRows, 0);
    }

    public <T> ColumnIndexedDoubleMatrix addColumn(
            @Nonnull T columnObj,
            @Nonnull DoubleMatrix columnVector
    ) {
        assert columnVector.columns == 1 && columnVector.rows == getRowNum();
        this.columnIndexMap.put(columnObj.toString(), nextFreeColumn);
        this.values = DoubleMatrix.concatHorizontally(this.values, columnVector);
        nextFreeColumn += 1;
        return this;
    }

    public int getColumnNum() {
        return this.values.columns;
    }

    public int getRowNum() {
        return this.values.rows;
    }

    public Set<String> getColumnNames() {
        return Set.copyOf(columnIndexMap.keySet());
    }

    /**
     * 获得列下标对应的列名
     */
    @Nonnull
    public String getColumnName(int columnIndex) {
        return columnIndexMap.inverse().get(columnIndex);
    }

    /**
     * 获得列名对应的列下标
     */
    @Nullable
    public Integer getColumnIndex(String columnName) {
        return columnIndexMap.get(columnName);
    }

    public ColumnIndexedDoubleMatrix addColumns(@Nonnull Map<?, DoubleMatrix> columns) {
        for (Map.Entry<?, DoubleMatrix> column : columns.entrySet()) {
            String columnName = column.getKey().toString();
            DoubleMatrix columnVector = column.getValue();
            addColumn(columnName, columnVector);
        }
        return this;
    }

    public <T> double get(int rowIndex, @Nonnull T columnObj) {
        Integer columnIndex = columnIndexMap.get(columnObj.toString());
        if (columnIndex == null)
            throw new IllegalArgumentException("invalid column name " + columnObj);
        return values.get(rowIndex, columnIndex);
    }

    public double get(int rowIndex, int columnIndex) {
        return values.get(rowIndex, columnIndex);
    }

    public <T> DoubleMatrix getColumn(@Nonnull T columnObj) {
        Integer columnIndex = columnIndexMap.get(columnObj.toString());
        if (columnIndex == null)
            throw new IllegalArgumentException("invalid column name " + columnObj);
        return this.values.getColumn(columnIndex);
    }

    public DoubleMatrix getColumn(int columnIndex) {
        return this.values.getColumn(columnIndex);
    }

    public <T> void setColumn(
            @Nonnull T columnObj,
            @Nonnull Function<DoubleMatrix, DoubleMatrix> remappingFunction
    ) {
        Integer columnIndex = columnIndexMap.get(columnObj.toString());
        if (columnIndex == null)
            throw new IllegalArgumentException("invalid column name " + columnObj.toString());
        DoubleMatrix oldColumn = this.values.getColumn(columnIndex);
        DoubleMatrix newColumn = remappingFunction.apply(oldColumn);
        this.values.putColumn(columnIndex, newColumn);
    }
}