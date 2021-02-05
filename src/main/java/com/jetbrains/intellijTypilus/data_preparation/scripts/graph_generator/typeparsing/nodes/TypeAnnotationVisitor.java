package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes;

interface TypeAnnotationVisitor {
    Object visitSubscriptAnnotation(String[] args);
    Object visitTupleAnnotation(String[] args);
    Object visitNameAnnotation(String[] args);
    Object visitListAnnotation(String[] args);
    Object visitAttributeAnnotation(String[] args);
    Object visitIndexAnnotation(String[] args);
    Object visitElipsisAnnotation(String[] args);
    Object visitNameConstantAnnotation(String[] args);
    Object visitUnknownAnnotation(String[] args);

}
