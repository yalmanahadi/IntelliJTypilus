package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import java.util.*;

public class GraphGenerator {
    LinkedHashMap<String, Object> graph = new LinkedHashMap<String, Object>();
    //LinkedHashMap<EdgeType, LinkedHashMap<Integer, Set<Integer>>> edges;
    ArrayList<Object> idToNode = new ArrayList<>();
    public LinkedHashMap<Object, Integer> nodeToId = new LinkedHashMap<>();
    public PsiFile psiFile = null;
    public PsiElement currentParentNode = null;
    public GraphGenerator(AnActionEvent e){
        this.psiFile = e.getData(LangDataKeys.PSI_FILE);

    }

    public GraphGenerator() {

    }


    public void build(){
        makeGraphStructure();

    }

    void makeGraphStructure(){
        LinkedHashMap<String, ArrayList<Integer>> edgeElement = new LinkedHashMap<>();

        this.graph.put("nodes", new ArrayList<String>());
        this.graph.put("edges", new LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.CHILD, edgeElement);
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.NEXT, edgeElement);
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.COMPUTED_FROM , edgeElement);
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.RETURNS_TO, edgeElement);
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.OCCURRENCE_OF, edgeElement);
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.SUBTOKEN_OF, edgeElement);
        this.graph.put("token-sequence", new ArrayList<Integer>());
        this.graph.put("supernodes", new LinkedHashMap<String, LinkedHashMap<String, Object>>());

    }
    public String toString(){
        return graph.toString();
    }
//
//    public static void main(String[] args) {
//        LinkedHashMap<Object, Integer> map = new LinkedHashMap<>();
//        map.put("abc", 123);
//        ArrayList<Integer> list = new ArrayList<>();
//        map.put(list, 234);
//        System.out.println(map.toString());
//        GraphGenerator graphGenerator = new GraphGenerator();
//        graphGenerator.build();
//        System.out.println(graphGenerator.nodeToId);
//
//    }

    String nodeToLabel(Object node) throws ClassNotFoundException {
        if (node instanceof String){
            return ((String) node).replace("\n", "");
        }
        else if (node instanceof TokenNode){
            return ((TokenNode) node).token.replace("\n", "");
        }
        else throw new ClassNotFoundException();
    }


//    public int nodeID(Object node){
//        if (this.nodeToId.get(node) == null){
//            System.out.println("herer");
//            if(node instanceof TokenNode) {
//                int idx = this.nodeToId.size();
//                assert (this.idToNode.size() == this.nodeToId.size());
//                this.nodeToId.put((TokenNode) node, idx);
//                this.idToNode.add((TokenNode) node);
//            }
//            else if (node instanceof PsiElement) {
//                nodeID((PsiElement) node);
//            }
//        }
//        return this.nodeToId.get(node);
//    }

    int nodeID(PsiElement node){
        //assert needed maybe

        if (this.nodeToId.get(node) == null){
            int idx = this.nodeToId.size();
            assert (idToNode.size() == nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
            return idx;
        }
        return this.nodeToId.get(node);
//        if (this.nodeToId.get(node) != null){
//        int idx = this.nodeToId.size();
//        if (this.idToNode.size() == this.nodeToId.size()) {
//                this.nodeToId.put(node, idx);
//                this.idToNode.add(node);
//            }
//        return idx;
    }

//    void addEdge(TokenNode fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
//        int fromNodeIdx = this.nodeID(fromNode);
//        int toNodeIdx = this.nodeID(toNode);
//        System.out.printf("adding edge type: %s", edgeType);
//        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
//    }

    void addEdge(PsiElement fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        System.out.println(((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).get(EdgeType.CHILD));
        if (((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).get(edgeType).containsKey(Integer.toString(fromNodeIdx))){
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        }
        else{
            LinkedHashMap<String, ArrayList<Integer>> newMap  = new LinkedHashMap<>();
            newMap.put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(edgeType, newMap);
        }
        System.out.println(this.graph);
    }

//    void addEdge(PsiElement fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
//        int fromNodeIdx = this.nodeID(fromNode);
//        int toNodeIdx = this.nodeID(toNode);
//        System.out.printf("adding edge type: %s", edgeType);
//        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
//        System.out.println(this.edges);
//    }
//    void addEdge(TokenNode fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
//        int fromNodeIdx = this.nodeID(fromNode);
//        int toNodeIdx = this.nodeID(toNode);
//        System.out.printf("adding edge type: %s", edgeType);
//        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
//        System.out.println(this.edges);
//    }


    public void visit(PsiElement node) {
//        System.out.println(node.getFirstChild().getNode().getPsi().getText());
        if (node instanceof PsiWhiteSpaceImpl){
            return;
        }

        if (this.currentParentNode != null) {
            System.out.println("in");
            System.out.println(this.currentParentNode.getNode().getElementType().getClass().getSimpleName());
            if (this.nodeToId.containsKey(this.currentParentNode) || (this.currentParentNode.getNode().getElementType().getClass().getSimpleName().equals("PyFileElementType"))) {
                System.out.println("here");
                this.addEdge(this.currentParentNode, node, EdgeType.CHILD);
            }
        }
        PsiElement parent = this.currentParentNode;
        this.currentParentNode = node;
        try {
//            System.out.printf("context %s", node.getClass().getSimpleName().replace("Impl", "")); /////////////

            String methodName = "visit" + node.getClass().getSimpleName().replace("Impl", "");

            Class<?> c = Class.forName("com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.GraphGenerator");
            Method method = c.getDeclaredMethod(methodName, PsiElement.class);
            method.invoke(this, node);


        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } finally {
            this.currentParentNode = parent;
        }
    }

    void visitPyFile(PsiElement file){
        for (PsiElement element : file.getChildren()){
            visit(element);
        }
    }
    void visitPyAssignmentStatement(PsiElement node) {
//        for(PsiElement element : node.getChildren()){
//            System.out.println("herer");
//            System.out.println(PyTargetExpression.class.getSimpleName());
//            System.out.println(element.getClass().getSimpleName());
//            if (element.getClass().getSimpleName().equals(PyTargetExpression.class.toGenericString())){
//
//            }
//        }
//        if (node.getAssignedValue() != null) {
//            System.out.println("visitied the assinment");
//            this.visit(statement.getAssignedValue());
//        }



    }



    void add_terminal(TokenNode tokenNode){
        //this.addEdge(this.currentParentNode, tokenNode, EdgeType.CHILD);
    }


}
