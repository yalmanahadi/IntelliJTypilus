package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;



import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes.TypeAnnotationNode;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;

public class SymbolInformation {
    String name;
    ArrayList<Integer> locations;
    HashMap<Integer, TypeAnnotationNode> annotatableLocations;
    String symbolType;

    SymbolInformation(String name,
                      ArrayList<Integer> locations,
                      HashMap<Integer, TypeAnnotationNode> annotatableLocations,
                      String symbolType){
        this.name = name;
        this.locations = locations;
        this.annotatableLocations =annotatableLocations;
        this.symbolType = symbolType;
    }
    static SymbolInformation create(String name, String symbolType){
        Pair<Integer, Integer> pair = null;
        return new SymbolInformation(name,
                new ArrayList<Integer>(),
                new HashMap<Integer, TypeAnnotationNode>(),
                symbolType);
    }



}
