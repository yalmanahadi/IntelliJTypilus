package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing;

import java.util.Arrays;

public abstract class TypeAnnotationNode {
    abstract int size();
    abstract Object accept_visitor(TypeAnnotationVisitor visitor, String[] args);
    public abstract String toString();
    public boolean hasattr(Object object, String fieldName) {
        return Arrays.stream(object.getClass().getFields())
                .anyMatch(f -> f.getName().equals(fieldName));
    }
}
