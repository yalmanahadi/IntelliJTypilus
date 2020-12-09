package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;


import java.util.*;

public class GraphGenerator {
    HashMap<String, Object> graph = new HashMap<String, Object>();
    HashMap<EdgeType, HashMap<Integer, Set<Integer>>> edges;
    ArrayList<Object> idToNode = new ArrayList<>();
    public HashMap<Object, Integer> nodeToId = new HashMap<>();
    PsiFile psiFile = null;
    PsiElement currentParentNode = null;
    public GraphGenerator(AnActionEvent e){
        this.psiFile = e.getData(LangDataKeys.PSI_FILE);

    }

    public GraphGenerator() {

    }


    public void build(){
        makeGraphStructure();

    }

    void makeGraphStructure(){
        HashMap<String, ArrayList<Integer>> edgeElement = new HashMap<String, ArrayList<Integer>>();

        this.graph.put("nodes", new ArrayList<String>());
        this.graph.put("edges", new HashMap<String, HashMap<String, ArrayList<Integer>>>());
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("CHILD", edgeElement);
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("NEXT", edgeElement);
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("COMPUTED_FROM" , edgeElement);
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("RETURNS_TO", edgeElement);
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("OCCURRENCE_OF", edgeElement);
        ((HashMap<String, HashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put("SUBTOKEN_OF", edgeElement);
        this.graph.put("token-sequence", new ArrayList<Integer>());
        this.graph.put("supernodes", new HashMap<String, HashMap<String, Object>>());

    }
    public String toString(){
        return graph.toString();
    }

    public static void main(String[] args) {
        HashMap<Object, Integer> map = new HashMap<>();
        map.put("abc", 123);
        ArrayList<Integer> list = new ArrayList<>();
        map.put(list, 234);
        System.out.println(map.toString());
        GraphGenerator graphGenerator = new GraphGenerator();
        graphGenerator.build();
        graphGenerator.nodeID(123);
        System.out.println(graphGenerator.nodeToId);

    }

    String nodeToLabel(Object node) throws ClassNotFoundException {
        if (node instanceof String){
            return ((String) node).replace("\n", "");
        }
        else if (node instanceof TokenNode){
            return ((TokenNode) node).token.replace("\n", "");
        }
        else throw new ClassNotFoundException();
    }


    public int nodeID(Object node){
        if (this.nodeToId.get(node) == null){
            System.out.println("herer");
            if(node instanceof TokenNode) {
                int idx = this.nodeToId.size();
                assert (this.idToNode.size() == this.nodeToId.size());
                this.nodeToId.put(node, idx);
                this.idToNode.add(node);
            }
            else if (node instanceof PsiElement) {
                nodeID((PsiElement) node);
            }
        }
        return this.nodeToId.get(node);
    }

    int nodeID(PsiElement node){
            int idx = this.nodeToId.size();
            assert (this.idToNode.size() == this.nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
            return idx;
    }

    void addEdge(TokenNode fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        System.out.printf("adding edge type: %s", edgeType);
        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
    }

    void addEdge(PsiElement fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        System.out.printf("adding edge type: %s", edgeType);
        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
        System.out.println(this.edges);
    }

    void addEdge(PsiElement fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        System.out.printf("adding edge type: %s", edgeType);
        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
        System.out.println(this.edges);
    }
    void addEdge(TokenNode fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        System.out.printf("adding edge type: %s", edgeType);
        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
        System.out.println(this.edges);
    }


    public void visit(PsiElement node){
        System.out.println(node.getFirstChild().getNode().getPsi().getText());
        PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);
            }
        };
        psiFile.accept(visitor);
        this.currentParentNode = psiFile.getFirstChild();
        if(this.currentParentNode != null){
            if(this.nodeToId.containsKey(this.currentParentNode) || (this.currentParentNode instanceof PyFile)) {
                System.out.println("here");
                this.addEdge(this.currentParentNode, node, EdgeType.CHILD);
            }
        }
        PsiElement parent = this.currentParentNode;
        this.currentParentNode = node;
        try{
            System.out.printf("context %s", node.getFirstChild().getNode()); /////////////
            //visit_AnnAssign(node.);

        }finally {

        }
    }
    void visitModule(PsiElement file){

    }
    void visitAnnAssign(PyAssignmentStatement node){
        if (node.getAssignedValue() != null){
            System.out.println("visitied the assinment");
            this.visit(node.getAssignedValue());
        }

    }



    void add_terminal(TokenNode tokenNode){
        this.addEdge(this.currentParentNode, tokenNode, EdgeType.CHILD);
    }


}
