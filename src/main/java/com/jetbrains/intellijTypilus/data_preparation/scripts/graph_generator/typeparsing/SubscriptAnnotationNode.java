package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing;

import com.intellij.vcs.log.Hash;
import com.sun.istack.Nullable;


public class SubscriptAnnotationNode extends TypeAnnotationNode {
    TypeAnnotationNode value;
    @Nullable TypeAnnotationNode slice;

    public SubscriptAnnotationNode(TypeAnnotationNode value, @Nullable TypeAnnotationNode slice){
        this.value = value;
        this.slice = slice;
    }
    @Override
    int size() {
        int size = 1 + this.value.size();
        if (this.slice != null){
            size += this.slice.size();
        }
        return size;
    }

    @Override
    Object accept_visitor(TypeAnnotationVisitor visitor, String[] args) {
        return visitor.visitSubscriptAnnotation(args);
    }

    @Override
    public String toString() {
        return this.value.toString() + '[' + this.slice.toString() + ']';
    }

    public int hash(){
        return this.value.hashCode() ^ (this.slice.hashCode() + 13);
    }

    SubscriptAnnotationNode parse(Object node) {
        return null;
    }
}
