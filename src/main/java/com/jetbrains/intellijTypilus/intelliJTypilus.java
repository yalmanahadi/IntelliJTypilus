package com.jetbrains.intellijTypilus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.*;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.util.ArrayList;

public class intelliJTypilus extends AnAction{

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        ArrayList<String> children = null;
        if (psiFile != null) {
            System.out.println(psiFile.getFirstChild().getText());
            //TypeEvalContext context =
            //context.allowCallContext(psiFile.getFirstChild());
            //context.getType((PyTypedElement) psiFile.getFirstChild());
        }






    }

}
