package com.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Objects;

class UseFSMDialog extends DialogWrapper {
    public final ComboBox<String> dropdown;
    public UseFSMDialog(String[] opts) {
        super(true);
        dropdown = new ComboBox<>(opts);
        setTitle("Use FSM");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Select an FSM: "));
        panel.add(dropdown);
        return panel;
    }
}

public class UseFSM extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project proj = e.getProject();
        PsiElement ele = Utilities.getPsiElement(e);
        if (proj == null || ele == null) return;
        PsiClass cls = Utilities.getClassBase(ele);
        if (cls == null) {
            Messages.showErrorDialog("No surrounding class is available", "Failed to Use FSM");
            return;
        }

        ArrayList<PsiField> enumVars = new ArrayList<>();
        for (PsiField field: cls.getAllFields()) {
                PsiType type = field.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null && resolved.isEnum()) enumVars.add(field);
                }
        }

        if (enumVars.isEmpty()) {
            Messages.showErrorDialog("No enums are available", "Failed to Use FSM");
            return;
        }

        String[] asArray = new String[enumVars.size()];
        for (int i = 0; i < enumVars.size(); i++) asArray[i] = enumVars.get(i).getName();

        UseFSMDialog dialog = new UseFSMDialog(asArray);
        boolean result = dialog.showAndGet();
        if (!result) return;
        String nameSelected = (String) dialog.dropdown.getSelectedItem();
        PsiField enumSelected = null;
        for (int i = 0; i < enumVars.size(); i++) {
            if (asArray[i].equals(nameSelected)) enumSelected = enumVars.get(i);
        }
        if (enumSelected == null) throw new RuntimeException("an enum should have been selected");

        PsiClass resolvedEnum = Objects.requireNonNull(((PsiClassType) enumSelected.getType()).resolve());
        StringBuilder switchCases = new StringBuilder("switch (" + nameSelected + ") {\n");
        for (PsiElement elem: resolvedEnum.getChildren()) {
            if (elem instanceof PsiEnumConstant) {
                switchCases.append("\tcase ").append(((PsiEnumConstant) elem).getName()).append(":\n\t\t// add code here\n\t\tbreak;\n");
            }
        }
        switchCases.append("}");
        PsiStatement switchCase = PsiElementFactory.getInstance(proj).createStatementFromText(switchCases.toString(), ele);
        WriteCommandAction.runWriteCommandAction(proj, () -> {
            ele.getParent().addBefore(switchCase, ele);
        });
    }
}
