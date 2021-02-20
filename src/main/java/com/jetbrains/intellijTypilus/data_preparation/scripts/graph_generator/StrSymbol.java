package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

import com.intellij.psi.PsiElement;

public class StrSymbol extends Symbol {
    String name;
    PsiElement psiElement;

    StrSymbol(String name, PsiElement psiElement){
        this.name = name;
        this.psiElement = psiElement;
    }

    int hash(){
        return this.name.hashCode();
    }

    public String toString(){
        return "Symbol: " + this.name;
    }
}
