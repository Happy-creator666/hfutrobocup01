package hfutinvincible.tool.parametermodification;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore.config.Config;
import rescuecore2.standard.entities.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class ModifyWindow extends JFrame implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;

    private JButton addParameter;
    private JButton decreaseParameter;
    private JList<String> Typewithtargets;
    private DefaultListModel<String> targetsModel;
    private Map<String, Double> scores;

    private JLabel selectTarget;
    private String currentSelectedKey = null; // 用于跟踪当前选中的目标键
    public ModifyWindow() {
        targetsModel = new DefaultListModel<>();
        scores = new HashMap<>();
    }

    @Override
    public void afterCalc(CalcContext context) {
        logger.info("----------------------begin------------------------");
        initWindow();
        setScores();
        initComponents();
        this.setVisible(true);
    }

    private void initWindow(){
        setTitle("ParameterModifier");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 300);
        Container container = getContentPane();
        container.setLayout(new BorderLayout());
    }
    private void setScores(){
        this.scores.clear();
        StandardEntity myType = agentInfo.me();
        Properties properties =new Properties();
        InputStream reader = null;
        try{
            String Filename =  "../config/params.properties";
            reader = Config.class.getClassLoader().getResourceAsStream(Filename);
            if(reader != null){
                properties.load(reader);
                if (myType instanceof FireBrigade){
                    Map<String,String> FbProperties =getPropertiesByPrefix(properties,"FbTargetProvider");
                    Map<String,String> FbtargetProperties = getPropertiesinrange(FbProperties);
                    FbtargetProperties.forEach((key,value) ->scores.put(key,Double.parseDouble(value)));
                } else if (myType instanceof PoliceForce) {
                    Map<String,String> PfProperties =getPropertiesByPrefix(properties,"PfTargetProvider");
                    Map<String,String> PftargetProperties = getPropertiesinrange(PfProperties);
                    PftargetProperties.forEach((key,value) ->scores.put(key,Double.parseDouble(value)));
                } else if (myType instanceof AmbulanceTeam) {
                    Map<String,String> AtProperties =getPropertiesByPrefix(properties,"AtTargetProvider");
                    Map<String,String> AttargetProperties = getPropertiesinrange(AtProperties);
                    AttargetProperties.forEach((key,value) ->scores.put(key,Double.parseDouble(value)));
                }
                else {
                    logger.info("myType is Civilian");
                }
            }
            else {
                throw new IOException("cannot open"+Filename);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void initComponents(){
        Typewithtargets = new JList<>(this.targetsModel);
        JScrollPane targetscroll = new JScrollPane(Typewithtargets);
        addParameter = new JButton("➕");
        decreaseParameter = new JButton("➖");

        setButtonSize(addParameter, 50, 50);
        setButtonSize(decreaseParameter, 50, 50);

        //添加显示所选目标组件
        selectTarget = new JLabel();
        selectTarget.setVisible(true);

        JPanel selectTargetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectTargetPanel.add(selectTarget);

        JPanel buttonPanelmodify = new JPanel();

        JPanel addAnddecreasePanel = new JPanel();
        addAnddecreasePanel.setLayout(new BorderLayout());

        JPanel Panelforsign = new JPanel();
        Panelforsign.setLayout(new BoxLayout(Panelforsign, BoxLayout.Y_AXIS));
        Panelforsign.setAlignmentX(Component.CENTER_ALIGNMENT);

        Panelforsign.add(Box.createVerticalGlue());
        Panelforsign.add(addParameter);
        Panelforsign.add(decreaseParameter);
        Panelforsign.add(Box.createVerticalGlue());

        addAnddecreasePanel.add(Panelforsign, BorderLayout.CENTER);
        Container container = getContentPane();
        container.add(targetscroll, BorderLayout.CENTER);
        container.add(buttonPanelmodify, BorderLayout.SOUTH);
        container.add(addAnddecreasePanel, BorderLayout.WEST);
        container.add(selectTargetPanel,BorderLayout.NORTH);

        container.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                addParameter.setVisible(false);
                decreaseParameter.setVisible(false);
                selectTarget.setVisible(false);
            }
        });
        Typewithtargets.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(! Typewithtargets.isSelectionEmpty()){
                    addParameter.setVisible(true);
                    decreaseParameter.setVisible(true);
                    selectTarget.setVisible(true);
                }
            }
        });

        Typewithtargets.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedValue = Typewithtargets.getSelectedValue();
                if (selectedValue != null && !selectedValue.isEmpty()) {
                    String[] parts = selectedValue.split("=");
                    if (parts.length == 2) {
                        currentSelectedKey = parts[0]; // 更新当前选中的目标键
                        selectTarget.setText("----Current Target is  " + currentSelectedKey+ "-----");
                    }
                }
            }
        });
        addParameter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 显示一个简单的信息对话框
                JOptionPane.showMessageDialog(ModifyWindow.this, "加号被点击了！", "信息", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        decreaseParameter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 显示一个简单的信息对话框
                JOptionPane.showMessageDialog(ModifyWindow.this, "减号被点击了！", "信息", JOptionPane.INFORMATION_MESSAGE);
            }
        });


//        addParameter.addActionListener(e -> changeParameters(0.02));
//
//        decreaseParameter.addActionListener(e -> changeParameters(-0.02));
    }

    private void setButtonSize(JButton button, int width, int height) {
        Dimension buttonDimension = new Dimension(width, height);
        button.setPreferredSize(buttonDimension);
        button.setMinimumSize(buttonDimension);
        button.setMaximumSize(buttonDimension);
    }


//    private void changeParameters(double delta) {
//        if (currentSelectedKey != null) {
//            BigDecimal value = BigDecimal.valueOf(scores.get(currentSelectedKey));
//            BigDecimal deltaBigDecimal = BigDecimal.valueOf(delta);
//
//            value = value.add(deltaBigDecimal).setScale(3, RoundingMode.HALF_UP);
//            scores.put(currentSelectedKey, value.doubleValue());
//            updateList();
//        }
//    }

//    private void updateList() {
//        targetsModel.clear();
//        scores.forEach((key, value) -> targetsModel.addElement(key + "=" + value));
//    }


    private Map<String,String> getPropertiesByPrefix(Properties properties,String prefix){
        Map<String, String> result = new HashMap<>();
        Set<String> keys = properties.stringPropertyNames();
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                String value = properties.getProperty(key);
                result.put(key, value);
            }
        }
        return result;
    }
    private Map<String,String>getPropertiesinrange(Map<String,String> properties){
        Map<String,String> result = new HashMap<>();
        properties.forEach((key,value) ->{
            try {
                double itsvalue = Double.parseDouble(value);
                if (itsvalue>=0 &&itsvalue<=1){
                    result.put(key,value);
                }
            }catch (NullPointerException e){
                logger.error("Ignore it");
            }
        });
        return result;
    }
}

