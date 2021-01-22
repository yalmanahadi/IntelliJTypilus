package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

import com.intellij.psi.PsiElement;
import org.javatuples.Unit;

public class Symbol {
    private PsiElement psiElement = null;
    Symbol(PsiElement element){
        this.psiElement = element;
    }
    public Unit<String> getSymbol(){
        return Unit.with(this.psiElement.getText());
    }
}
