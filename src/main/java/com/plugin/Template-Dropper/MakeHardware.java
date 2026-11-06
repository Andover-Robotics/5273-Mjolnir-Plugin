package com.plugin;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

class HardwareOptions extends DialogWrapper {
    ButtonGroup className = new ButtonGroup();
    JTextField fieldName = new JTextField();
    JTextField deviceName = new JTextField();
    public HardwareOptions() {
        super(true);
        fieldName.setPreferredSize(new Dimension(100, 30));
        deviceName.setPreferredSize(new Dimension(100, 30));
        setTitle("Add FTC Hardware");
        init();
    }

    private JPanel addRadioButtons(ButtonGroup group, JRadioButton... radio) {
        JPanel panel = new JPanel();
        for (JRadioButton btn: radio) {
            panel.add(btn);
            group.add(btn);
        }
        return panel;
    }

    @Override
    public JPanel createCenterPanel() {
        JRadioButton opt1 = new JRadioButton("Servo");
        JRadioButton opt2 = new JRadioButton("DcMotor", true);
        JPanel panel = addRadioButtons(className, opt1, opt2);

        JPanel comp = new JPanel();
        comp.setLayout(new GridLayout(3, 2));
        comp.add(new JLabel("Hardware type:"));
        comp.add(panel);
        comp.add(new JLabel("Field name:"));
        comp.add(fieldName);
        comp.add(new JLabel("Device name:"));
        comp.add(deviceName);
        return comp;
    }
}

public class MakeHardware extends AnAction {
    private void ensureImport(Project project, PsiFile file, String name) {
        PsiImportList list = ((PsiJavaFile) file).getImportList();
        boolean needImport = list == null;
        if (!needImport) {
            needImport = true;
            for (PsiImportStatement state: list.getImportStatements()) {
                if (name.equals(state.getQualifiedName())) needImport = false;
            }
        }
        if (needImport) {
            PsiJavaFile dummyFile = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(
                    "Dummy.java",
                    JavaLanguage.INSTANCE,
                    "import " + name + ";class Dummy{}"
            );
            PsiImportStatement cls = Objects.requireNonNull(dummyFile.getImportList()).getImportStatements()[0];
            if (list != null) list.add(cls);
            else file.addAfter(cls, null);
        }
    }

    private void addHardware(Project proj, PsiClass el, boolean type, String fieldName, String deviceName) {
        PsiElementFactory fac = JavaPsiFacade.getElementFactory(proj);
        PsiFile file = el.getContainingFile();
        String typeName = type ? "Servo" : "DcMotor";
        el.add(fac.createFieldFromText("private final " + typeName + " " + fieldName + ";", el));
        boolean isConstructed = false;

        PsiMethod ctor = null;
        for (PsiMethod method: el.getMethods()) {
            if (method.isConstructor()) ctor = method;
        }

        if (ctor == null) {
            isConstructed = true;
            ctor = fac.createConstructor("\npublic " + el.getName() + "(){\n}");
        }

        String hwi = "com.qualcomm.robotcore.hardware.HardwareMap";
        PsiParameter argName = null;
        for (PsiParameter param: ctor.getParameterList().getParameters()) {
            if (param.getType().getCanonicalText().equals(hwi)) {
                argName = param;
            }
        }
        if (argName == null) {
            PsiParameter param = fac.createParameterFromText("HardwareMap hardwareMap", el);
            ensureImport(proj, file, hwi);
            ctor.getParameterList().add(param);
            argName = param;
        }

        PsiStatement statement = fac.createStatementFromText(
                fieldName + " = " + argName.getName() + ".get(" + typeName + ".class, \"" + deviceName + "\");",
                el
        );
        if (ctor.getBody() != null) ctor.getBody().add(statement);
        if (isConstructed) el.add(ctor);
        ensureImport(proj, file,  "com.qualcomm.robotcore.hardware." + typeName);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project proj = e.getProject();
        PsiElement res = Utilities.getPsiElement(e);
        if (proj == null || res == null) return;
        PsiClass cls = Utilities.getClassBase(res);
        if (cls == null) {
            Messages.showErrorDialog("No surrounding class is available", "Failed to Create FTC Hardware");
            return;
        }

        HardwareOptions options = new HardwareOptions();
        boolean result = options.showAndGet();
        if (!result) return;

        boolean isServo = options.className.getElements().nextElement().isSelected();
        WriteCommandAction.runWriteCommandAction(proj, () -> addHardware(proj, cls, isServo, options.fieldName.getText(), options.deviceName.getText()));
    }
}
