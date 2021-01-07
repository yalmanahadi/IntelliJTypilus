package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

public class TokenNode {
    String token;
    int startOffset;
    TokenNode(String token, int startOffset){
        this.token = token;
        this.startOffset = startOffset;
    }

    TokenNode(String token){
       this.token = token;
    }

    public String toString(){
        return this.token;
    }
}
