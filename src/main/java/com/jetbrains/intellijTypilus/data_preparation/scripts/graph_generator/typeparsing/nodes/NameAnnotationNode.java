package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes;

public class NameAnnotationNode extends TypeAnnotationNode {
    public String identifier;

    NameAnnotationNode(String identifier){
        this.identifier = identifier;
    }

    @Override
    int size() {
        return 1;
    }

    @Override
    Object accept_visitor(TypeAnnotationVisitor visitor, String[] args) {
        return visitor.visitNameAnnotation(args);
    }

    @Override
    public String toString() {
        return this.identifier;
    }

//    NameAnnotationNode parse(Object node) {
//        if (hasattr(node, "identifier")) return NameAnnotationNode(node.identifier);
//    }
}
