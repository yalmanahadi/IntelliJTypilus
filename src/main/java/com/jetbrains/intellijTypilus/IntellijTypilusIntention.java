package com.jetbrains.intellijTypilus;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.GraphGenerator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyAssignmentStatementImpl;
import com.jetbrains.python.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

public class IntellijTypilusIntention extends PsiElementBaseIntentionAction {

    @NotNull
    public String getText(){
        return "Get Typilus Type Hints";
    }

    /**
     * This function should add the type annotations as per the predictions of Typilus once the
     * preprocessing task is completed
     * Currently it will just do the same as the Action button does for getting type hints
     * which just prints the generated Python code graph
     * @param project
     * @param editor
     * @param element
     * @throws IncorrectOperationException
     */

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiFile psiFile = element.getContainingFile();
        if (psiFile != null) {
            GraphGenerator graphGenerator = new GraphGenerator(psiFile);
            graphGenerator.build();
            graphGenerator.visit(psiFile);
            System.out.println(graphGenerator.nodeToId);
        }
        final PsiFileFactory factory = PsiFileFactory.getInstance(project);
        if (element.getContext() instanceof PyTargetExpressionImpl) {
            PsiElement annotation = factory.createFileFromText("newFile", PythonLanguage.getInstance(), ": Any");
            assert psiFile != null;
            psiFile.addAfter(annotation, element.getContext());
        }
        else if (element.getContext() instanceof PyFunction){
            PsiElement annotation = factory.createFileFromText("newFile", PythonLanguage.getInstance(), "-> Any");
            PsiElement afterParameterList = PsiTreeUtil.getChildOfType(element.getContext(), PyParameterListImpl.class);
            assert psiFile != null;
            psiFile.addAfter(annotation, afterParameterList);
        }

    }


    /**
     * The intention action should only be available where type annotations can be provided
     * This is when assigning a variable or when defining a function
     */
    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiElement context = element.getContext();
        if (context instanceof PyTargetExpressionImpl  && context.getContext() instanceof PyAssignmentStatementImpl ||
            context instanceof PyFunction){
            return true;
        }

        return false;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "IntellijTypilusIntention";
    }
}
