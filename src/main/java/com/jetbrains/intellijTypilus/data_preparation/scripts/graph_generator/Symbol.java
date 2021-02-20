package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

import com.intellij.psi.PsiElement;
import org.javatuples.Unit;

public class Symbol {
    private PsiElement psiElement = null;
    private String name = null;
    Symbol(PsiElement element){
        this.psiElement = element;
    }

    public Symbol() {

    }

    public Unit<String> getSymbol(){
        return Unit.with(this.psiElement.getText());
    }
    Symbol (String name){
        this.name = name;
    }
}
