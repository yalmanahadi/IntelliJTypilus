package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;



import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.TypeAnnotationNode;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SymbolInformation {
    String name;
    ArrayList<Pair<Integer, Integer>> locations;
    HashMap<Pair<Integer, Integer>, TypeAnnotationNode> annotatableLocations;
    String symbolType;

    SymbolInformation(String name,
                      ArrayList<Pair<Integer, Integer>> locations,
                      HashMap<Pair<Integer, Integer>, TypeAnnotationNode> annotatableLocations,
                      String symbolType){
        this.name = name;
        this.locations = locations;
        this.annotatableLocations =annotatableLocations;
        this.symbolType = symbolType;
    }
    static SymbolInformation create(String name, String symbolType){
        Pair<Integer, Integer> pair = null;
        return new SymbolInformation(name,
                new ArrayList<Pair<Integer, Integer>>(),
                new HashMap<Pair<Integer, Integer>, TypeAnnotationNode>(),
                symbolType);
    }



}
