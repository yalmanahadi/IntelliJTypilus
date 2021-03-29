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
import com.jetbrains.python.psi.impl.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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
        }
        final PsiFileFactory factory = PsiFileFactory.getInstance(project);

        if (element.getContext() instanceof PyTargetExpressionImpl && element.getContext().getContext() instanceof PyAssignmentStatementImpl) {
            if (((PyTargetExpressionImpl) element.getContext()).getAnnotation() == null) {

                PyTargetExpressionImpl target = (PyTargetExpressionImpl) element.getContext();
                PyAssignmentStatementImpl assignmentStatement = (PyAssignmentStatementImpl) target.getContext();

                if (assignmentStatement != null && assignmentStatement.getAssignedValue() != null) {

                    //change this annotation text to match the correct annotation text from Typilus predictions
                    //putting the equal symbol after the annotation helps
                    String annotation = ": Any = ";
                    PyFileImpl annotationFile = (PyFileImpl)
                            factory.createFileFromText("newFile", PythonLanguage.getInstance(),
                                    element.getText() + annotation +
                                            assignmentStatement.
                                                    getAssignedValue().getText());
                    assert psiFile != null;

                    //annotationFile will have one child which will be the annotated assignment statement
                    assignmentStatement.replace(annotationFile.getFirstChild());
                }
            }
        }
        else if (element.getContext() instanceof PyFunctionImpl){
            PyFunctionImpl function = (PyFunctionImpl) element.getContext();
            if (function.getAnnotation() == null) {
                String funcText = function.getText();
                PsiElement afterParameterList = PsiTreeUtil.getChildOfType(function, PyParameterListImpl.class);
                assert afterParameterList != null;
                String annotation = "-> Any";
                int annotationIndex = afterParameterList.getTextLength() + afterParameterList.getTextOffset();
                funcText = funcText.substring(0, afterParameterList.getTextOffset() + afterParameterList.getTextLength())
                        + annotation + funcText.substring(annotationIndex);
                PsiElement annotationFile = factory.createFileFromText("newFile", PythonLanguage.getInstance(), funcText);
                assert psiFile != null;

                //annotationFile will have only one child which will be the new annotated function
                function.replace(annotationFile.getFirstChild());
            }
        }

        else if (element.getContext() instanceof PyNamedParameterImpl){
            PyNamedParameterImpl namedParameter = (PyNamedParameterImpl) element.getContext();
            if (namedParameter.getAnnotation() == null){
                String parameterName = namedParameter.getName();
                String annotation = ": Any ";
                PsiElement annotationFile = factory.createFileFromText("newfile",
                        PythonLanguage.getInstance(), parameterName + annotation);
                assert psiFile != null;

                namedParameter.replace(annotationFile.getFirstChild());
            }
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
            context instanceof PyFunctionImpl ||
            context instanceof PyNamedParameterImpl){
            return true;
        }

        return false;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "IntellijTypilusIntention";
    }
}
