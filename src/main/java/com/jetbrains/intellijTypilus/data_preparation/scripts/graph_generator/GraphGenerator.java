package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.lineIndent.IndentCalculator;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.jetbrains.python.psi.impl.PyIfPartIfImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
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
    public ArrayList<String> ignoredNodes = new ArrayList<>(Arrays.asList(
            "PyIfPartIfImpl"));
    String INDENT = "<INDENT>";
    String DEDENT = "<DEDENT>";
    String INLINE = "<NL>";

    public GraphGenerator(AnActionEvent e){
        this.psiFile = e.getData(LangDataKeys.PSI_FILE);
//        this.context = TypeEvalContext.userInitiated(psiFile.getProject(), psiFile);


    }

    public GraphGenerator() {

    }


    public void build(){
        makeGraphStructure();

    }

    void makeGraphStructure(){


        this.graph.put("nodes", new ArrayList<String>());
        this.graph.put("edges", new LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.CHILD, new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.NEXT, new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.COMPUTED_FROM , new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.RETURNS_TO, new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.OCCURRENCE_OF, new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.SUBTOKEN_OF, new LinkedHashMap<String, ArrayList<Integer>>());
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




    public void visit(PsiElement node) {
        System.out.println(node.getClass());
        if (node instanceof PsiWhiteSpaceImpl){ // node.getText().length() == 1){
            return;
        }


        if (this.currentParentNode != null) {
            if (this.nodeToId.containsKey(this.currentParentNode) ||
                    (this.currentParentNode
                            .getClass()
                            .getSimpleName()
                            .equals("PyFileImpl"))) {

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
        int i = 0;
        for(PsiElement child : node.getChildren()){

            //System.out.println((child.getNode().findChildByType(PyTokenTypes.)));
            //TODO add func for i > 1
            i += 1;
        }
    }



    void addTerminal(TokenNode tokenNode){
        this.addEdge(this.currentParentNode, tokenNode, EdgeType.CHILD);
    }

    void visitPyIfStatement(PsiElement node){
        for (PsiElement part : node.getChildren()) {
            this.visit(part);
        }
    }

    void visitPyBoolLiteralExpression(PsiElement node){
        System.out.println(node.getText());
        this.addTerminal(new TokenNode(node.getText()));
    }

    void visitPyIfPartIf(PsiElement node){
        for (PsiElement child : node.getChildren()){
            this.visit(child);
        }
    }

    void visitPyStatementList(PsiElement node){
        PsiElement[] statements = node.getChildren();
        this.addTerminal(new TokenNode(this.INDENT));
        int i = 0;
        for(PsiElement statement : statements){
            this.visit(statement);
            if (i < statements.length - 1){
                this.addTerminal(new TokenNode(this.INLINE));
            }
            if (i > 0){
                this.addEdge(statements[i-1], statement, EdgeType.NEXT);
            }
            i += 1;
        }
        this.addTerminal(new TokenNode(this.DEDENT));
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


    public int nodeID(TokenNode node){
        if (this.nodeToId.get(node) == null){
            int idx = this.nodeToId.size();
            assert (this.idToNode.size() == this.nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
        }
        return this.nodeToId.get(node);
    }

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
        if (((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        }
        else {
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"))
                    .get(edgeType)
                    .put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
        System.out.println(this.graph);
    }

    void addEdge(PsiElement fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        if (((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"))
                .get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"))
                    .get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"))
                    .get(edgeType)
                    .put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
        System.out.println(this.graph);
    }
//    void addEdge(TokenNode fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
//        int fromNodeIdx = this.nodeID(fromNode);
//        int toNodeIdx = this.nodeID(toNode);
//        System.out.printf("adding edge type: %s", edgeType);
//        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
//        System.out.println(this.edges);
//    }

}
