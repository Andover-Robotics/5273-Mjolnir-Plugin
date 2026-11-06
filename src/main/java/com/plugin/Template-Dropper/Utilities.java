package com.plugin;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Utilities {
    private static boolean isRealSourceClass(@NotNull PsiClass psiClass) {
        return !psiClass.isInterface();
    }
    static @Nullable PsiClass getClassBase(@Nullable PsiElement el) {
        PsiClass base = el instanceof PsiClass ? (PsiClass) el : PsiTreeUtil.getParentOfType(el, PsiClass.class);
        while (base != null && !isRealSourceClass(base)) {
            base = PsiTreeUtil.getParentOfType(base, PsiClass.class);
        }
        return base;
    }
    static @Nullable PsiElement getPsiElement(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) return null;
        return file.findElementAt(editor.getCaretModel().getOffset());
    }
}
