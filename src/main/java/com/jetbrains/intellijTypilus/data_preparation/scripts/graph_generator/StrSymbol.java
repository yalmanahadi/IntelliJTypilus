package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

public class StrSymbol {
    String name;

    StrSymbol(String name){
        this.name = name;
    }

    int hash(){
        return this.name.hashCode();
    }

    public String toString(){
        return "Symbol: " + this.name;
    }
}
