package com.plugin;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

public class OpModeDialog extends DialogWrapper {
    public final JTextField className = new JTextField();
    public final JTextField opmodeName = new JTextField();
    public final JTextField groupName = new JTextField();
    public final ButtonGroup opmodeType = new ButtonGroup();
    public final ButtonGroup languageType = new ButtonGroup();
    public final ButtonGroup classType = new ButtonGroup();

    public OpModeDialog() {
        super(true);
        className.setPreferredSize(new Dimension(100, 30));
        opmodeName.setPreferredSize(new Dimension(100, 30));
        groupName.setPreferredSize(new Dimension(100, 30));
        setTitle("Add FTC OpMode");
        init();
    }

    private JPanel addRadioButtons(ButtonGroup group, JRadioButton... buttons) {
        JPanel panel = new JPanel();
        for (JRadioButton button : buttons) {
            group.add(button);
            panel.add(button);
        }
        return panel;
    }

    @Override
    protected JComponent createCenterPanel() {
        JRadioButton opt1 = new JRadioButton("Teleop", true);
        JRadioButton opt2 = new JRadioButton("Auto");
        JPanel groupPanel = addRadioButtons(opmodeType, opt1, opt2);

        JRadioButton optl1 = new JRadioButton("Java", true);
        JRadioButton optl2 = new JRadioButton("Kotlin");
        JPanel groupPanel2 = addRadioButtons(languageType, optl1, optl2);

        JRadioButton optc1 = new JRadioButton("LinearOpMode", true);
        JRadioButton optc2 = new JRadioButton("OpMode");
        JPanel groupPanel3 = addRadioButtons(classType, optc1, optc2);

        JPanel comp = new JPanel(new GridLayout(6, 2));
        comp.add(new JLabel("Class name:"));
        comp.add(className);
        comp.add(new JLabel("Opmode name (optional):"));
        comp.add(opmodeName);
        comp.add(new JLabel("Opmode group name (optional):"));
        comp.add(groupName);
        comp.add(new JLabel("Opmode type:"));
        comp.add(groupPanel);
        comp.add(new JLabel("Language:"));
        comp.add(groupPanel2);
        comp.add(new JLabel("Base class:"));
        comp.add(groupPanel3);
        return comp;
    }
}
