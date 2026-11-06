package com.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Enumeration;

public class MakeOpMode extends AnAction {
    private void addFile(
            //params
            String name,
            PsiDirectory dir,
            Project project,
            String opmode,
            String group,
            boolean teleop,
            boolean isJava,
            boolean isLinear
    )
    {
                String suffix = isJava ? "java" : "kt"; // Determine file extension based on language selection (Java or Kotlin)
                FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(suffix); // Retrieve the correct FileType for the file extension to ensure proper file creation in the IDE
                PsiFileFactory factory = PsiFileFactory.getInstance(project); // Factory for creating PSI files programmatically
                String text = OpModeGenerator.createOpMode(isJava, name, opmode, group, teleop, isLinear); // Generates OpMode class source code
                dir.add(factory.createFileFromText(name + "." + suffix, fileType, text));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        OpModeDialog dialog = new OpModeDialog();
        boolean status = dialog.showAndGet();
        if (!status) return;

        String name = dialog.className.getText();
        if (name.isEmpty()) return;

        Project project = e.getProject();
        if (project == null) return;

        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (!(element instanceof PsiDirectory dir)) return;

        boolean teleopSelected = hasSelectedButton(dialog.opmodeType);
        boolean isJava = hasSelectedButton(dialog.languageType);
        boolean isLinear = hasSelectedButton(dialog.classType);

        WriteCommandAction.runWriteCommandAction(project, () -> addFile(
                name,
                dir,
                project,
                dialog.opmodeName.getText(),
                dialog.groupName.getText(),
                teleopSelected,
                isJava,
                isLinear
        ));
    }

    private boolean hasSelectedButton(ButtonGroup group) {
        Enumeration<AbstractButton> buttons = group.getElements();
        while (buttons.hasMoreElements()) {
            if (buttons.nextElement().isSelected()) return true;
        }
        return false;
    }
}
