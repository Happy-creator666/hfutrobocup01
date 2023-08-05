package hfutinvincible.debug;

import rescuecore2.worldmodel.EntityID;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class InspectorPanel extends JTable {

    private EntityID targetEntityId = null;
    private List<String> inspectorNames;
    private List<Function<EntityID, String>> inspectFuncs;
    private InspectorTableModel model;

    public InspectorPanel() {
        inspectorNames = new ArrayList<>();
        inspectFuncs = new ArrayList<>();
        model = new InspectorTableModel();
        setModel(model);
    }

    /**
     * <p>注册一个 inspector</p>
     * <p>一个 inspector 可以看成一个函数, 输入一个 EntityID, 输出对应 Entity 的一个属性.
     * 比如 HpInspector 就是用于检视智能体生命值的 Inspector, 在输入是一个智能体的 EntityID 时,
     * 应该返回此智能体的 Hp, 如果是其他 Area 之类的 Entity, 则返回空字符串.</p>
     * <p>在点击调试器图上的某个元素时, 会遍历所有已注册的的 inspectors，收集所有的属性显示在左侧栏中.</p>
     * @param colName 这个 inspector 检视的属性名 (比如 Hp, Damage 等)
     * @param inspectFunc 检视函数, 输入待检视的 Entity, 返回属性值的字符串表示
     */
    public void registerInspector(String colName, Function<EntityID, String> inspectFunc) {
        inspectorNames.add(colName);
        inspectFuncs.add(inspectFunc);
    }

    /**
     * 设置当前检视的 EntityID、
     */
    public void setTargetEntityId(EntityID targetEntityId) {
        this.targetEntityId = targetEntityId;
        model.fireTableStructureChanged();
        model.fireTableDataChanged();
    }

    private class InspectorTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return inspectorNames.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) { // 名字列
                return inspectorNames.get(rowIndex);
            } else { // 值列
                if (targetEntityId == null)
                    return "";
                else return inspectFuncs.get(rowIndex).apply(targetEntityId);
            }
        }
    }
}
