package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyBaseElementImpl;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import org.javatuples.Unit;

public class Symbol {
    private PsiElement psiElement = null;
    private String name = null;
    private TokenNode tokenNode = null;

    Symbol(PsiElement element){
        this.psiElement = element;
    }
    Symbol (TokenNode tokenNode){
        this.tokenNode = tokenNode;
    }


    public PsiElement getPsiElement(){
        return this.psiElement;
    }

    public String getSymbol(){
        if (this.psiElement != null){
            if (this.psiElement instanceof PyFunctionImpl ||
                this.psiElement instanceof PyClassImpl) {
                return ((PyBaseElementImpl<?>) this.psiElement).getName();
            }
            return this.psiElement.getText();
        }
        else if (this.tokenNode != null){
            return this.tokenNode.toString();
        }
        return null;
    }
}
