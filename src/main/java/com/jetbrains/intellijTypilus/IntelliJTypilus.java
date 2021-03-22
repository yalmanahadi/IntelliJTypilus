package com.jetbrains.intellijTypilus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.*;
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.GraphGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.util.ArrayList;

public class IntelliJTypilus extends AnAction{

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile != null) {
            GraphGenerator graphGenerator = new GraphGenerator(e);
            graphGenerator.build();
            graphGenerator.visit(psiFile);
            //TypeEvalContext context =
            //context.allowCallContext(psiFile.getFirstChild());
            //context.getType((PyTypedElement) psiFile.getFirstChild());
        }






    }

}
