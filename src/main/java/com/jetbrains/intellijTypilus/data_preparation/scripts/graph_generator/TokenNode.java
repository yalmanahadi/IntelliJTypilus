package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

public class TokenNode {
    String token;
    int lineno;
    int colOffset;
    TokenNode(String token, int lineno, int colOffset){
        this.token = token;
        this.lineno = lineno;
        this.colOffset = colOffset;
    }

    TokenNode(String token){
       this.token = token;
    }

    public String toString(){
        return this.token;
    }
}
