package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;

public class Any<T> {
    private T t;

    public Any(T t) {
        this.t = t;
    }
    public T get(){
        return t;
    }

    public static void main(String[] args){
        Any any = new Any("abc");
        System.out.println(any.get());
        System.out.println(any.getClass().getSimpleName());
    }
}

