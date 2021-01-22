package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiElementFilter;
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.TypeAnnotationNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;



import java.util.*;

public class GraphGenerator {
    LinkedHashMap<String, Object> graph = new LinkedHashMap<String, Object>();
    //LinkedHashMap<EdgeType, LinkedHashMap<Integer, Set<Integer>>> edges;
    ArrayList<Object> idToNode = new ArrayList<>();
    ArrayList<SearchScope> scopes = new ArrayList<>();
    TokenNode prevTokenNode = null;
    ArrayList<TokenNode> backboneSequence = new ArrayList<>();
    public LinkedHashMap<Object, Integer> nodeToId = new LinkedHashMap<>();
    public PsiFile psiFile = null;
    public PsiElement currentParentNode = null;
    HashMap<PsiElement, TokenNode> extractedTokenNodes = new HashMap<>();
    HashMap<PsiElement, Symbol> extractedSymbols = new HashMap<>();
    String INDENT = "<INDENT>";
    String DEDENT = "<DEDENT>";
    String INLINE = "<NL>";
    HashMap<PyElementType, String> CMPOPS = new HashMap<PyElementType, String>(){{

        put(PyTokenTypes.EQEQ, "==");
        put(PyTokenTypes.LE, "<=");
        put(PyTokenTypes.GE, ">=");
        put(PyTokenTypes.NE, "!=");
        put(PyTokenTypes.IN_KEYWORD, "in");
        put(PyTokenTypes.IS_KEYWORD, "is");
        put(PyTokenTypes.NOT_KEYWORD, "not");
    }};

    HashMap<PyElementType, String> BINOPS = new HashMap<PyElementType, String>(){{
       put(PyTokenTypes.PLUS, "+");
       put(PyTokenTypes.PLUSEQ, "+=");
       put(PyTokenTypes.MINUS, "-");
       put(PyTokenTypes.MINUSEQ, "-=");
       put(PyTokenTypes.MULT, "*");
       put(PyTokenTypes.MULTEQ, "*=");
       put(PyTokenTypes.DIV, "/");
       put(PyTokenTypes.DIVEQ, "/=");
       put(PyTokenTypes.FLOORDIV, "//");
       put(PyTokenTypes.FLOORDIVEQ, "//=");
       put(PyTokenTypes.PERC, "%");
       put(PyTokenTypes.PERCEQ, "%=");
       put(PyTokenTypes.LTLT, "<<");
       put(PyTokenTypes.LTLTEQ, "<<=");
       put(PyTokenTypes.GTGT, ">>");
       put(PyTokenTypes.GTGTEQ, ">>=");
       put(PyTokenTypes.OR, "|");
       put(PyTokenTypes.OREQ, "|=");
       put(PyTokenTypes.AND, "&");
       put(PyTokenTypes.ANDEQ, "&=");
       put(PyTokenTypes.XOR, "^");
       put(PyTokenTypes.XOREQ, "^=");
       put(PyTokenTypes.EXP, "**");
       put(PyTokenTypes.EXPEQ, "**=");
       //MatMult is missing
    }};


    public GraphGenerator(AnActionEvent e){
        this.psiFile = e.getData(LangDataKeys.PSI_FILE);
//        this.context = TypeEvalContext.userInitiated(psiFile.getProject(), psiFile);
        assert this.psiFile != null;
        this.scopes.add(this.psiFile.getUseScope());

    }

    public GraphGenerator() {}

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
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.LAST_LEXICAL_USE, new LinkedHashMap<String, ArrayList<Integer>>());
        ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges")).put(EdgeType.NEXT_USE, new LinkedHashMap<String, ArrayList<Integer>>());
        this.graph.put("token-sequence", new ArrayList<Integer>());
        this.graph.put("supernodes", new LinkedHashMap<String, LinkedHashMap<String, Object>>());
    }

    public String toString(){
        return graph.toString();
    }

    private void getScopeElements(PsiElement node){
        PsiElement[] items = PsiTreeUtil.collectElements(node, new PsiElementFilter() {
            @Override
            public boolean isAccepted(@NotNull PsiElement psiElement) {
                if (psiElement.getNavigationElement() != null) {
                }
                return true;
            }
        });
    }

    public void visit(PsiElement node) {
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
            this.visit(element);
        }
    }

    void visitPyAssignmentStatement(PsiElement node) {
        if(node instanceof PyAssignmentStatementImpl){
            PyAssignmentStatementImpl assignNode = (PyAssignmentStatementImpl) node;
            Iterator<PyExpression> iterator = Arrays.asList((assignNode.getRawTargets())).iterator();
            while(iterator.hasNext()){
                PsiElement item = iterator.next();
                this.visit(item);
                System.out.println(item.getText());
                //this.visitVariableLike(item, item.getTextOffset(), true, null, node.getUseScope());
            }
            this.addTerminal(new TokenNode("="));
            this.visit(assignNode.getAssignedValue());
        }

//        SearchScope scope = new LocalSearchScope(node.getFirstChild());
//        System.out.println("-----");
//        for( PsiReference i : ReferencesSearch.search(node.getFirstChild()).findAll()){
//            System.out.println(i.getElement().getUseScope().getClass());
//        }
//
//        System.out.println("-----");
//        visitPyTargetExpression(node.getFirstChild());
//        System.out.println(node.getResolveScope().getDisplayName());
//        System.out.println(node.getParent().getUseScope());
//        this.addTerminal(new TokenNode("="));
//        this.visit(node.getLastChild());
//        for(PsiElement child : node.getChildren()){
//                if (child.getText().equals("=")){
//                    //this.addTerminal(new TokenNode("="));
//                }
//                this.visit(child);
//            //TODO add func for i > 1
//        }
    }

    void visitPyTargetExpression(PsiElement target){
        if (target.getChildren().length == 0) { //when just assigning a raw value to a variable
            PyTargetExpressionImpl expression = (PyTargetExpressionImpl) target;
            this.visitVariableLike(target, target.getTextOffset(), true, null, target.getUseScope());
            System.out.println(target.getFirstChild().getClass());
        }
        else if (target.getChildren().length > 0){

        }

        System.out.println(((PyTargetExpressionImpl)target).getName());
    }


    void visitPyReferenceExpression(PsiElement node){
        this.visitVariableLike(node, node.getTextOffset(), false, null, node.getUseScope());
    }
    
    void visitNameAnnotatable(PsiElement node, int startOffset, boolean canAnnotateHere, TypeAnnotationNode typeAnnotationNode, SearchScope nodeScope){
        this.addEdge(this.currentParentNode, node, EdgeType.CHILD);
        PsiElement parent = this.currentParentNode;
        this.currentParentNode = node;
        try{
            System.out.println(123);
            this.visitVariableLike(node, startOffset, canAnnotateHere, typeAnnotationNode, node.getUseScope());
            System.out.println(321);
        }finally {
            this.currentParentNode = parent;
        }
    }


    void visitVariableLike(PsiElement node, int startOffset, boolean canAnnotateHere, TypeAnnotationNode typeAnnotationNode, SearchScope nodeScope){
        HashMap<String, Object> results = getSymbolForName(node, startOffset);
        TokenNode newNode = (TokenNode)results.get("node");
        Symbol symbol = (Symbol)results.get("symbol");
        if (newNode != null){
            this.addEdge(newNode, symbol, EdgeType.OCCURRENCE_OF);
        }
    }

    HashMap<String, Object> getSymbolForName(PsiElement node, int startOffset){
        HashMap<String,Object> results = new HashMap<>();
        TokenNode newNode = new TokenNode(node.getText(), startOffset);
        this.addTerminal(newNode);
        this.extractedTokenNodes.put(node, newNode);
        this.extractedSymbols.put(node, new Symbol(node));
        //this.addTerminal(newNode);
        //TODO special underscored items
        results.put("node", newNode);

        Symbol symbol = getScopeSymbol(node);

        if (symbol != null) results.put("symbol", symbol);

        System.out.println(results);
        return results;
    }
    //returns [name, node, symbol, symbolType]
//    HashMap<String, Object> getSymbolForName(PsiElement node, int startOffset){
//        HashMap<String,Object> results = new HashMap<>();
//        if (name instanceof String){
//            TokenNode node = new TokenNode((String) name, startOffset);
//            this.addTerminal(node);
//            //TODO special underscored items
//            results.put("node", node);
//
//            TokenNode symbol = getScopeSymbol(name)
//
//            if (symbol != null) results.put("symbol", symbol);
//       }
//        System.out.println(results);
//        return results;
//    }

    Symbol getScopeSymbol(PsiElement node){
        for (int i = this.scopes.size(); i >= 0; i--) {
            PsiReference reference = node.getReference();
            assert reference != null;
            return this.extractedSymbols.get(reference.resolve());
        }
        return null;
    }


    void visitPyNumericLiteralExpression(PsiElement node){
        this.addTerminal(new TokenNode(node.getText()));
    }



    void addTerminal(TokenNode tokenNode){
        this.addEdge(this.currentParentNode, tokenNode, EdgeType.CHILD);
        if (this.prevTokenNode != null){
            this.addEdge(this.prevTokenNode, tokenNode, EdgeType.NEXT);
        }
        this.backboneSequence.add(tokenNode);
        this.prevTokenNode = tokenNode;

    }

    void visitPyIfStatement(PsiElement node){
        boolean elifPart = false;
        for (PsiElement part : node.getChildren()) {
            if (part instanceof PyIfPartIfImpl) {
                visitPyIfPartIf(part);
            }
            else if (part instanceof PyIfPartElifImpl){
                elifPart = true;
                this.addTerminal(new TokenNode("else"));
                this.addTerminal(new TokenNode(this.INDENT));
                visitPyIfPartIf(part);
            }
            else if (part instanceof PyElsePartImpl){
                this.addTerminal(new TokenNode("else"));
                this.visitPyStatementList(part);
            }
        }
        if (elifPart) this.addTerminal(new TokenNode(this.DEDENT));
    }

    void visitPyBoolLiteralExpression(PsiElement node){
        this.addTerminal(new TokenNode(node.getText()));
    }

    void visitPyBinaryExpression(PsiElement node){
        PyBinaryExpressionImpl castedNode = (PyBinaryExpressionImpl)node;
        this.visit(castedNode.getLeftExpression());
        if (CMPOPS.containsKey(castedNode.getOperator())) {
            this.addTerminal(new TokenNode(this.CMPOPS.get(castedNode.getOperator())));
        }
        else if (BINOPS.containsKey(castedNode.getOperator())){
            this.addTerminal(new TokenNode(this.BINOPS.get(castedNode.getOperator())));
        }
        this.visit(castedNode.getRightExpression());
    }

    void visitPyIfPartIf(PsiElement node){
        this.addTerminal(new TokenNode("if"));
        for (PsiElement child : node.getChildren()){
            this.visit(child);
        }
    }

//    void visitPyIfPartElif(PsiElement node){
//        this.addTerminal(new TokenNode("else"));
//
//    }

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

    int nodeID(Symbol node) {
        //assert needed maybe

        if (this.nodeToId.get(node) == null) {
            int idx = this.nodeToId.size();
            assert (idToNode.size() == nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
            return idx;
        }
        return this.nodeToId.get(node);

//    void addEdge(TokenNode fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
//        int fromNodeIdx = this.nodeID(fromNode);
//        int toNodeIdx = this.nodeID(toNode);
//        System.out.printf("adding edge type: %s", edgeType);
//        this.edges.get(edgeType).get(fromNodeIdx).add(toNodeIdx);
//    }
    }



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

    void addEdge(TokenNode fromNode, TokenNode toNode, EdgeType edgeType) { //need to add edges part
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

    void addEdge(TokenNode fromNode, Symbol toNode, EdgeType edgeType) { //need to add edges part
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

}
