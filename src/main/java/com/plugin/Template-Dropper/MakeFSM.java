package com.plugin;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;

class FSMDialog extends DialogWrapper {
    public final ArrayList<String> fsmElements = new ArrayList<>();
    public final JTextField fsmName = new JTextField();
    private final JPanel dropdown = new JPanel();
    public FSMDialog() {
        super(true);
        setTitle("Add FSM");
        // should have at least one element in a fsm
        fsmElements.add("");
        fsmName.setPreferredSize(new Dimension(250, 30));
        init();
    }

    private void update() {
        dropdown.removeAll();
        dropdown.setLayout(new BoxLayout(dropdown, BoxLayout.Y_AXIS));
        for (int _i = 0; _i < fsmElements.size(); _i++) {
            int i = _i;

            JTextField fsmElem = new JTextField();
            JButton del = new JButton("-");
            del.setForeground(JBColor.RED);
            del.addActionListener(e -> {
                fsmElements.remove(i);
                update();
            });
            del.setPreferredSize(new Dimension(20, 30));
            fsmElem.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    fsmElements.set(i, fsmElem.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    // no op
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    fsmElements.set(i, fsmElem.getText());
                }
            });
            fsmElem.setText(fsmElements.get(i));
            fsmElem.setPreferredSize(new Dimension(300, 30));
            JPanel row = new JPanel();
            row.add(fsmElem);
            row.add(del);
            dropdown.add(row);
        }
        dropdown.revalidate();
        dropdown.repaint();
        Window dialog = SwingUtilities.getWindowAncestor(dropdown);
        if (dialog != null) dialog.pack();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JPanel panel1 = new JPanel();
        JPanel panel2 = new JPanel();
        panel2.add(new JLabel("FSM Name:"));
        panel1.add(panel2);
        panel1.add(fsmName);
        panel.add(panel1);
        panel.add(new JLabel("FSM enum values:"));
        panel.add(dropdown);
        JButton btn = new JButton("Add an enum value");
        btn.addActionListener(e -> {
            fsmElements.add("");
            update();
        });
        panel.add(btn);
        update();
        return panel;
    }
}

public class MakeFSM extends AnAction {
    private void addFSM(Project proj, PsiClass cls, String fsmName, ArrayList<String> fsmElements) {
        StringBuilder enumContents = new StringBuilder("enum " + fsmName + " {");
        for (String item: fsmElements) {
            enumContents.append("\t").append(item).append(",\n");
        }
        enumContents.append("}");
        PsiJavaFile file = (PsiJavaFile) PsiFileFactory.getInstance(proj).createFileFromText(
                "Dummy.java",
                JavaLanguage.INSTANCE,
                "class Dummy {\n" + enumContents + "\n}"
        );
        PsiClass newEnum = file.getClasses()[0].getInnerClasses()[0];
        cls.add(newEnum);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project proj = e.getProject();
        PsiElement res = Utilities.getPsiElement(e);
        if (proj == null || res == null) return;
        PsiClass cls = Utilities.getClassBase(res);
        if (cls == null) {
            Messages.showErrorDialog("No surrounding class is available", "Failed to Add FSM");
            return;
        }

        FSMDialog dialog = new FSMDialog();
        boolean result = dialog.showAndGet();
        if (!result) return;

        WriteCommandAction.runWriteCommandAction(proj, () -> addFSM(proj, cls, dialog.fsmName.getText(), dialog.fsmElements));
    }
}
