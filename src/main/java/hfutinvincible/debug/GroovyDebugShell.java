package hfutinvincible.debug;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hfutinvincible.brain.AgentBrain;

import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

class GroovyDebugShell extends JPanel {

    public GroovyDebugShell() {
        Box innerBox = Box.createVerticalBox();
        // groovy 引擎
        Binding binding = new Binding();
        StringWriter out = new StringWriter(); // 输出流
        binding.setVariable("agentBrainMap", AgentBrain.AGENT_BRAIN_MAP);
        binding.setVariable("out", out);
        GroovyShell groovyShell = new GroovyShell(binding);
        // 输入脚本的组件
        innerBox.add(new JLabel("Script Path:"));
        JFileChooser fileChooser = new JFileChooser(".");
        innerBox.add(fileChooser);
        // 显示输出的组件
        innerBox.add(new JLabel("Stdout & stderr:"));
        JTextArea outputArea = new JTextArea();
        outputArea.setColumns(50);
        outputArea.setLineWrap(true);
        outputArea.setEditable(false);
        outputArea.setBackground(Color.LIGHT_GRAY);
        outputArea.setFont(new Font("Ubuntu Mono", Font.PLAIN, 14));
        JScrollPane outputAreaScrollable = new JScrollPane(outputArea);
        outputAreaScrollable.setPreferredSize(new Dimension(500, 300));
        innerBox.add(outputAreaScrollable);
        // 执行按钮
        JButton executeButton = new JButton();
        executeButton.setText("Execute");
        executeButton.addActionListener(_ignored -> {
            // 读取脚本内容
            String selectedFile = fileChooser.getSelectedFile().getAbsolutePath();
            String script = null;
            try {
                script = Files.readString(Paths.get(selectedFile), StandardCharsets.UTF_8);
            } catch (Exception e) {
                outputArea.setText("Failed to read script file from " + selectedFile);
                return;
            }
            try { // 捕获所有异常
                groovyShell.evaluate(script);
            } catch (Exception e) {
                e.printStackTrace(new PrintWriter(out));
            }
            out.append('\n');
            outputArea.setText(out.toString());
        });
        innerBox.add(executeButton);
        add(innerBox);
    }

    public static void openGroovyDebugShell() {
        JFrame frame = new JFrame("Groovy Debug Shell");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new GroovyDebugShell());
        frame.pack();
        frame.setVisible(true);
    }
}