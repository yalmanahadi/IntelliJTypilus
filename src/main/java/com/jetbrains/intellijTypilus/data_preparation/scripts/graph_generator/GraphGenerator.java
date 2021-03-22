package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiElementFilter;
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes.TypeAnnotationNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.*;


import java.lang.reflect.Type;
import java.util.*;

public class GraphGenerator {
    LinkedHashMap<String, Object> graph = new LinkedHashMap<String, Object>();
    //LinkedHashMap<EdgeType, LinkedHashMap<Integer, Set<Integer>>> edges;
    ArrayList<Object> idToNode = new ArrayList<>();
    ArrayList<HashMap<String, Pair<PsiElement, Symbol>>> scopes = new ArrayList<>();
    ArrayList<HashMap<String, Pair<PsiElement, Symbol>>> allScopes = new ArrayList<>();
    PsiElement returnScope = null;
    TokenNode prevTokenNode = null;
    ArrayList<TokenNode> backboneSequence = new ArrayList<>();
    public LinkedHashMap<Object, Integer> nodeToId = new LinkedHashMap<>();
    public PsiFile psiFile = null;
    public PsiElement currentParentNode = null;
    LinkedHashMap<Symbol, SymbolInformation> variableLikeSymbols = new LinkedHashMap<>();
    LinkedHashMap<Symbol, TokenNode> lastLexicalUse = new LinkedHashMap<>();
    HashMap<String, Pair<PsiElement, Symbol>> currentExtractedSymbols = new HashMap<>();
    HashMap<String, Pair<PsiElement, Symbol>> instanceAttributes = new HashMap<>();
    HashMap<String, Pair<PsiElement, Symbol>> currentExtractedAttributes = new HashMap<>();
    String INDENT = "<INDENT>";
    String DEDENT = "<DEDENT>";
    String NLINE = "<NL>";
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

    Pattern IDENTIFIER_REGEX = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    List<String> pyKeywords = Arrays.asList("False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield");



    public GraphGenerator(AnActionEvent e){
        this.psiFile = e.getData(LangDataKeys.PSI_FILE);
        assert this.psiFile != null;
        this.makeNewScope(); //create top level scope
    }

    public GraphGenerator(PsiFile psiFile){
        this.psiFile = psiFile;
        assert this.psiFile != null;
        this.makeNewScope(); //create top level scope
    }

    public GraphGenerator() {}

    public void build(){
        makeGraphStructure();
    }

    void makeGraphStructure(){
        this.graph.put("nodes", new ArrayList<String>());
        this.graph.put("edges", new LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>());
//        for(EdgeType i: EdgeType.values()){
//            ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"))
//                    .put(i, new LinkedHashMap<String, ArrayList<Integer>>());
//        }
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

//    private PsiElement[] getScopeElements(PsiElement node){
//        PsiElement[] items = PsiTreeUtil.collectElements(node.getFirstChild(), new PsiElementFilter() {
//            @Override
//            public boolean isAccepted(@NotNull PsiElement psiElement) {
//                PyBaseElementImpl pyBaseElement = (PyBaseElementImpl) psiElement;
//                if (((PyBaseElementImpl<?>) psiElement).getElementType() != null) {
//                    return true;
//                }
//                return false;
//            }
//        });
//        return items;
//    }

    void makeNewScope(){
        HashMap<String, Pair<PsiElement, Symbol>> newScope = new HashMap<>();
        this.scopes.add(newScope);
        this.allScopes.add(newScope);
        this.currentExtractedSymbols = newScope;

    }

    void exitScope(){
        System.out.println("scccc");
        System.out.println(this.scopes);
        this.currentExtractedSymbols = this.scopes.get(this.scopes.size()-2);
        this.scopes.remove(this.scopes.size()-1);
    }

    public void visit(PsiElement node) {

        //ignore whitespaces
        if (node instanceof PsiWhiteSpaceImpl){
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


    /**
     * Visits a Python PSI file to parse and generate the graph for
     *
     * @param file The Python PSI file to visit
     */

    void visitPyFile(PsiElement file) {
        for (PsiElement element : file.getChildren()) {
            this.visit(element);
        }

        this.addSubtokenOfEdges();

        //add supernodes to graph
        for (Map.Entry item : this.variableLikeSymbols.entrySet()) {
            System.out.println("awda");
            System.out.println(item.getKey().toString() + ((SymbolInformation) item.getValue()).name);
            System.out.println(parseSymbolInfo((SymbolInformation) item.getValue()));
            ((LinkedHashMap<String, LinkedHashMap<String, Object>>) this.graph.get("supernodes"))
                    .put(this.nodeToId.get(item.getKey()).toString(),
                            parseSymbolInfo((SymbolInformation) item.getValue()));
        }

        //add backbone sequence to graph
        for (TokenNode item : this.backboneSequence){
            ((ArrayList<Integer>) this.graph.get("token-sequence")).add(nodeToId.get(item));
        }

        //add all the nodes to the graph
        int i = 0;
        while (i < this.idToNode.size()) {
            try {
                ((ArrayList<String>) this.graph.get("nodes")).add(nodeToLabel(this.idToNode.get(i)));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            i++;
        }


        System.out.println(this.graphToJson());
        System.out.println("nodess");
        System.out.println(this.nodeToId);
    }

    /**
     * Serializes the main graph to a Json object
     * @return json representation of the graph as a string
     */
    String graphToJson(){
        //Convert the complex "supernodes" filed first
        Type supernodesType = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Object>>>(){}.getType();
        Gson supernodesGson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        String supernodes = supernodesGson.toJson(this.graph.get("supernodes"), supernodesType);

        //Create the json of the main graph and replace the supernodes value in for the final json representation
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String json = gson.toJson(this.graph);
        json = json.replace("\"supernodes\":{}", "\"supernodes\":" + supernodes);
        return json;
    }




    LinkedHashMap<String, Object> parseSymbolInfo(SymbolInformation sInfo){
        System.out.println("pawsss");
        System.out.println(sInfo.name + sInfo.symbolType);
        Integer firstAnnotatableLocation = Collections.min(sInfo.annotatableLocations.keySet());
        String annotation = null;

        return new LinkedHashMap<String, Object>(){{
            put("name", sInfo.name);
            put("annotation", annotation);
            put("location", firstAnnotatableLocation);
            put("type", sInfo.symbolType);
        }};
    }

    void visitPyAssignmentStatement(PsiElement node) {
        if(node instanceof PyAssignmentStatementImpl){
            PyAssignmentStatementImpl assignNode = (PyAssignmentStatementImpl) node;
            Iterator<PyExpression> iterator = Arrays.asList((assignNode.getRawTargets())).iterator();
            int i = 0;
            while(iterator.hasNext()){
                PsiElement target = iterator.next();
                if (isAttribute(target)){
//                    if (!this.currentExtractedSymbols.containsKey(target.getText())) {
//                        this.currentExtractedSymbols.put(target.getText(), new Pair<>(target, new Symbol(target)));
//                    }
                    //need to parse type annotation and pass it in the typeAnnotationNode argument below
                    this.visitVariableLike(target, target.getTextOffset(), true, null);
                }
                else {
                    this.visit(target);
                }
                if (i > 0) {
                    this.addEdge(target,assignNode.getAssignedValue(), EdgeType.NEXT);
                }
                if (!isAttribute(target)) {
                    this.addEdge(target, assignNode.getAssignedValue(), EdgeType.COMPUTED_FROM);
                }
                if (i <  assignNode.getRawTargets().length - 1){
                    this.addTerminal(new TokenNode(","));
                }
                //this.visitVariableLike(item, item.getTextOffset(), true, null, node.getUseScope());
                i += 1;
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
        PyTargetExpressionImpl targetExpression = (PyTargetExpressionImpl) target;

        //for attributes
//        if (targetExpression.isQualified()){
//            if (targetExpression.getContainingClass() != null && targetExpression.getQualifier().textMatches("self")){
//                this.visit(targetExpression.getFirstChild()); //first child of this will be reference expression 'self'
//                this.addTerminal(new TokenNode(".", targetExpression.getTextOffset()));
//                this.addEdge(this.currentParentNode, new TokenNode("Attribute"), EdgeType.CHILD);
//            }
//        }
        //if (targetExpression.getChildren().length == 0) { //when just assigning a raw value to a variable
//        if (!this.currentExtractedSymbols.containsKey(targetExpression.getReferencedName())) {
//            this.currentExtractedSymbols.put(targetExpression.getReferencedName(), new Pair<>(targetExpression, new Symbol(targetExpression)));
//        }
        if (isAttribute(targetExpression)) {
            this.visitVariableLike(targetExpression.getFirstChild(), targetExpression.getFirstChild().getTextOffset(), true, null);
        }
        else {
            this.visitVariableLike(targetExpression, targetExpression.getTextOffset(), true, null);
        }
        //}
        //else if (targetExpression.getChildren().length > 0){}




    }


    void visitPyReferenceExpression(PsiElement referenceExpressionNode) {
        if (referenceExpressionNode instanceof PyReferenceExpressionImpl) {
            PyReferenceExpressionImpl referenceExpression = (PyReferenceExpressionImpl) referenceExpressionNode;

            if (!this.currentExtractedSymbols.containsKey(referenceExpression.getReferencedName())) {
                this.currentExtractedSymbols.put(referenceExpression.getReferencedName(), new Pair<>(referenceExpression, new Symbol(referenceExpression)));
            }
            this.visitVariableLike(referenceExpression, referenceExpression.getTextOffset(), false, null);


            System.out.println("refery");
            System.out.println(referenceExpression.getText());
//            PsiElement parent = referenceExpression.getParent();
//            if (parent instanceof PyTargetExpressionImpl ||
//                parent instanceof PyReferenceExpressionImpl){
//                if (getScopeSymbol(parent) == null){
//
//                    this.visitVariableLike(referenceExpression, referenceExpression.getTextOffset(), false, null);
//                }
//            }
//            else if (getScopeSymbol(referenceExpression) != null){
//                this.currentExtractedSymbols.put(referenceExpression.getReferencedName(), new Pair<>(referenceExpression, new Symbol(referenceExpression)));
//                this.visitVariableLike(referenceExpression, referenceExpression.getTextOffset(), false, null);
//            }

        }
    }
    
    void visitNameAnnotatable(PsiElement node, int startOffset, boolean canAnnotateHere, TypeAnnotationNode typeAnnotationNode, SearchScope nodeScope){
        this.addEdge(this.currentParentNode, node, EdgeType.CHILD);
        PsiElement parent = this.currentParentNode;
        this.currentParentNode = node;
        try{
            this.visitVariableLike(node, startOffset, canAnnotateHere, typeAnnotationNode);
        }finally {
            this.currentParentNode = parent;
        }
    }


    void visitVariableLike(PsiElement node, int startOffset, boolean canAnnotateHere, TypeAnnotationNode typeAnnotation){
        HashMap<String, Object> results = getSymbolForName(node, startOffset);
        TokenNode resultNode = null;
        Symbol resultSymbol = null;
        if (results.get("node") instanceof TokenNode) {
            resultNode = (TokenNode) results.get("node");
        }
        if (results.get("symbol") instanceof Symbol) {
            resultSymbol = (Symbol) results.get("symbol");
        }
        if (resultNode != null && resultSymbol != null){
            this.addEdge(resultNode, resultSymbol, EdgeType.OCCURRENCE_OF);

            SymbolInformation symbolInformation = this.variableLikeSymbols.get(resultSymbol);
            if (symbolInformation == null && results.get("symbolType") != null){
                System.out.println("chawss");
                System.out.println((String)results.get("name") + (String) results.get("symbolType"));
                symbolInformation = SymbolInformation.create((String)results.get("name"),
                        (String) results.get("symbolType"));
                this.variableLikeSymbols.put((Symbol) results.get("symbol"), symbolInformation);
            }
            if (symbolInformation != null) {
                symbolInformation.locations.add(startOffset);
                if (canAnnotateHere) symbolInformation.annotatableLocations.put(startOffset, typeAnnotation);
            }

            //generally last lexical use is on nodes of type TokenNode
            TokenNode lastLexicalUseNode = this.lastLexicalUse.get(resultSymbol);
            if (lastLexicalUseNode != null){
                this.addEdge(lastLexicalUseNode, resultNode, EdgeType.LAST_LEXICAL_USE);
            }
            this.lastLexicalUse.put(resultSymbol, resultNode);
            System.out.println("last");
            System.out.println(lastLexicalUse.size());
            System.out.println(lastLexicalUse);
        }

    }




    HashMap<String, Object> getSymbolForName(PsiElement node, int startOffset) {
        HashMap<String, Object> results = new HashMap<>();
        Symbol symbol = null;
        if (!isAttribute(node)) {
            TokenNode newNode = new TokenNode(node.getText(), startOffset);
            this.addTerminal(newNode);
            //TODO special underscored items
            results.put("node", newNode);

            symbol = getScopeSymbol(node);
            System.out.println("here" + node.getText());
            System.out.println(this.currentExtractedSymbols);
            results.put("name", node.getText());
        }

        //If nodes are of form X.Y (including attributes with form: self.X)
        else if (isAttribute(node)) {
            PyReferenceExpression prefix = (PyReferenceExpressionImpl) node.getFirstChild();
            results.put("name", node.getText());
            if (prefix.getReferencedName() != null ) {
                this.visit(prefix);
                this.addTerminal(new TokenNode(".", node.getTextOffset()));

                // can appear in targetExpression and ReferenceExpression
                if (node instanceof PyReferenceExpressionImpl) {
                    PyReferenceExpression refSuffix = (PyReferenceExpressionImpl) node;
                    this.addTerminal(new TokenNode(refSuffix.getName()));
                }
                else if (node instanceof PyTargetExpressionImpl) {
                    PyTargetExpression targetSuffix = (PyTargetExpressionImpl) node;
                    this.addTerminal(new TokenNode(targetSuffix.getName()));

                }

                //PyAttribute does not exist in PSI
                //Only added to match output from Typilus
                //Adding COMPUTED_FROM edge here as well
                TokenNode newNode = new TokenNode("PyAttribute", startOffset);
                results.put("node", newNode);
                this.addEdge(this.currentParentNode, newNode, EdgeType.CHILD);
                symbol = getScopeSymbol(node);
                if (symbol == null) {
                    this.addTerminal(new TokenNode(node.getText(), startOffset));
                    this.currentExtractedSymbols.put(node.getText(), new Pair<>(node, new Symbol(node)));
                }
                if (node.getParent() instanceof PyAssignmentStatementImpl) {
                    PyAssignmentStatementImpl parentAssignNode = (PyAssignmentStatementImpl) node.getParent();
                    if (parentAssignNode.getAssignedValue() != null) {
                        this.addEdge(newNode, parentAssignNode.getAssignedValue(), EdgeType.COMPUTED_FROM);
                    }
                }

            }

        }

        if (symbol != null) {
            results.put("symbol", symbol);
            PsiElement symbolsPsiType = symbol.getPsiElement();
            if (symbolsPsiType instanceof PyTargetExpressionImpl ||
                symbolsPsiType instanceof PyReferenceExpressionImpl) {
                results.put("symbolType", "variable");
            } else if (symbolsPsiType instanceof PyFunctionImpl ||
                    symbolsPsiType instanceof PyClassImpl) {
                results.put("symbolType", "classOrFunction");
                //TODO: imported nad parameter
            }
        }
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
        if (!isAttribute(node)) {
            for (int i = this.scopes.size(); i > 0; i--) {
                PsiReference reference = node.getReference();
                // resolve a variable reference, or find function, or find parameters
                if (reference != null ||
                        node.getParent().getClass() == PyFunctionImpl.class ||
                        node.getClass() == PyNamedParameterImpl.class
                ) {
                    System.out.println(node.getText() + "innnn");
                    for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.scopes.get(i - 1).entrySet()) {
                        if (entry.getKey().equals(node.getText())) {
                            System.out.println("scopeee " + entry.getKey() + " " + entry.getValue().getFirst().getTextOffset());
                            System.out.println("scopeee " + node.getText() + " " + node.getTextOffset());
                            return entry.getValue().getSecond(); //returns the corresponding Symbol
                        }
                    }
                }

            }
        }
        else if (isAttribute(node)){
           for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.instanceAttributes.entrySet()){
               if (entry.getKey().equals(node.getText())){
                   return entry.getValue().getSecond();
               }
           }
        }

        System.out.println("printing null node");
        System.out.println(node.getText());
        System.out.println(allScopes);
        return null;
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
                for (PsiElement child: part.getChildren()){
                    visit(child);
                }
            }
        }
        if (elifPart) this.addTerminal(new TokenNode(this.DEDENT));
    }

    void visitPyBoolLiteralExpression(PsiElement node){
        this.addTerminal(new TokenNode(node.getText()));
    }

    void visitPyBinaryExpression(PsiElement node){
        PyBinaryExpressionImpl binaryExpression = (PyBinaryExpressionImpl)node;
        this.visit(binaryExpression.getLeftExpression());
        if (CMPOPS.containsKey(binaryExpression.getOperator())) {
            this.addTerminal(new TokenNode(this.CMPOPS.get(binaryExpression.getOperator())));
        }
        else if (BINOPS.containsKey(binaryExpression.getOperator())){
            this.addTerminal(new TokenNode(this.BINOPS.get(binaryExpression.getOperator())));
        }
        this.visit(binaryExpression.getRightExpression());
    }

    void visitPyIfPartIf(PsiElement node){
        this.addTerminal(new TokenNode("if"));
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
                this.addTerminal(new TokenNode(this.NLINE));
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
            System.out.println("token node type");
            System.out.println(((TokenNode) node).token);
            return ((TokenNode) node).token.replace("\n", "");
        }
        else if (node instanceof PsiElement){
            System.out.println("psi type");
            System.out.println((PsiElement) node);
            return ((PsiElement) node).toString();
        }
        else if (node instanceof Symbol){
            System.out.println("symbol type");
            System.out.println(((Symbol) node).getSymbol());
            return ((Symbol) node).getSymbol().replace("\n", "");
        }
        else {
            System.out.println(node.getClass());
            throw new ClassNotFoundException();
        }
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

    //Region: Subtoken of edges
    void addSubtokenOfEdges(){
        Set<TokenNode> allIdentifierLikeNodes = new HashSet<>();
        LinkedHashMap<String, TokenNode> subtokenNodes = new LinkedHashMap<>();
        for (Map.Entry object : this.nodeToId.entrySet()){
            if (object.getKey() instanceof TokenNode) {
                if (this.isIdentifierNode(object.getKey())) {
                    allIdentifierLikeNodes.add((TokenNode)object.getKey());
                }
            }
        }

        for (TokenNode node : allIdentifierLikeNodes){
            List<String> splitIdentfierIntoParts = new ArrayList<String>(Arrays.asList(node.token.split("_")));
            splitIdentfierIntoParts.removeAll(Arrays.asList("",null));
            System.out.println("treeee");
            System.out.println(splitIdentfierIntoParts);

            for (String subtoken : splitIdentfierIntoParts){
                TokenNode subtokenDummyNode = subtokenNodes.get(subtoken);
                if (subtokenDummyNode == null){
                    subtokenDummyNode = new TokenNode(subtoken);

                    subtokenNodes.put(subtoken, subtokenDummyNode);
                }
                this.addEdge(subtokenDummyNode, node, EdgeType.SUBTOKEN_OF);

            }
        }
    }

    boolean isIdentifierNode(Object node){
        final PsiFileFactory factory = PsiFileFactory.getInstance(this.psiFile.getProject());
        if (!(node instanceof TokenNode)){
            return false;
        }
        if (!this.IDENTIFIER_REGEX.matcher(((TokenNode) node).token).matches()){
            return false;
        }
        if (node.equals(this.INDENT) || node.equals(this.DEDENT) || node.equals(this.NLINE)){
            return false;
        }
        if (pyKeywords.contains(((TokenNode) node).token)){
            return false;
        }
        if (((TokenNode) node).token.equals("")){
            return false;
        }
        if (((TokenNode) node).token.equals("PyAttribute")){
            return false;
        }
        return true;
    }



    int nodeID(Symbol node) {
        if (this.nodeToId.get(node) == null) {
            int idx = this.nodeToId.size();
            assert (idToNode.size() == nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
            return idx;
        }
        return this.nodeToId.get(node);
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

    void addEdge(TokenNode fromNode, PsiElement toNode, EdgeType edgeType) { //need to add edges part
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



    //Region: PyFunctions

    void visitPyFunction(PsiElement node){
        this.visitFunction(node, false);
    }

    void visitFunction(PsiElement node, boolean async){
        if (node instanceof PyFunctionImpl){
            PyFunctionImpl funcNode = (PyFunctionImpl) node;
            if (funcNode.getDecoratorList() != null) {
                for (PyDecorator decorator : funcNode.getDecoratorList().getDecorators()) {
                    this.addTerminal(new TokenNode("@"));
                    this.visit(decorator);
                }
            }
            if (async){
                this.addTerminal(new TokenNode("async"));
            }
            this.addTerminal(new TokenNode("def"));

            //TODO: returns
            if (!this.currentExtractedSymbols.containsKey(funcNode.getName())) {
                this.currentExtractedSymbols.put(funcNode.getName(), new Pair<>(funcNode, new Symbol(funcNode)));
            }
            this.visitVariableLike(funcNode.getNameIdentifier() , node.getTextOffset(), true, null);
            PsiElement oldReturnScope = this.returnScope;
            makeNewScope();
            try {
                this.addTerminal(new TokenNode("("));
                this.visit(funcNode.getParameterList());
                this.addTerminal(new TokenNode(")"));
                this.returnScope = node;
                this.visitPyStatementList(funcNode.getStatementList());
            }
            finally {
                this.returnScope = oldReturnScope;
                exitScope();
            }
        }

    }

    void visitPyParameterList(PsiElement node){
        PyParameterListImpl parameterListNode = (PyParameterListImpl) node;
        if (parameterListNode != null) {
            int i = 0;
            for (PyParameter parameter :parameterListNode.getParameters()){
                this.visit(parameter);
                //TODO: if default not Null?
                this.addTerminal(new TokenNode(","));
                if (i > 0){
                    this.addEdge(parameterListNode.getParameters()[i-1], parameter, EdgeType.NEXT);
                }
                i += 1;
            }
        }
    }

    void visitPyNamedParameter(PsiElement node){
        PyNamedParameterImpl namedParameter = (PyNamedParameterImpl) node;
        if (namedParameter != null){
            TypeAnnotationNode annotationNode = null;
            if (namedParameter.getAnnotation() != null){
                //TODO: parseTypeAnnotationNode(node.annotation)
                //TODO: parseTypeComment(node.type_comment)
            }
            if (!this.currentExtractedSymbols.containsKey(namedParameter.getName())) {
                this.currentExtractedSymbols.put(namedParameter.getName(), new Pair<>(namedParameter, new Symbol(namedParameter)));
            }
            this.visitVariableLike(namedParameter, namedParameter.getTextOffset(), true, null);
        }
    }

    void visitPyReturnStatement(PsiElement returnNode){
        this.addEdge(returnNode, this.returnScope, EdgeType.RETURNS_TO);
        this.addTerminal(new TokenNode("return"));
        for (PsiElement returnElement : returnNode.getChildren()){
            this.visit(returnElement);
        }
    }

    void visitPyLambdaExpression(PsiElement lambdaNode){
        this.addTerminal(new TokenNode("lambda"));
        if (lambdaNode instanceof  PyLambdaExpressionImpl) {
            PyLambdaExpressionImpl lambdaExpression = (PyLambdaExpressionImpl) lambdaNode;
            try {
                this.makeNewScope();
                this.visit(lambdaExpression.getParameterList());
                this.addTerminal(new TokenNode(":"));
                this.visit(lambdaExpression.getBody());
                this.exitScope();
            } catch (IllegalArgumentException ignored) {

            }
        }
    }

    //End Region: PyFunctions


    //Region: Control Flow

    void visitPyBreakStatement(PsiElement breakNode){
        this.addTerminal(new TokenNode("break"));
    }

    void visitPyContinueStatement(PsiElement continueNode){
        this.addTerminal(new TokenNode("continue"));
    }

    void visitPyForStatement(PsiElement pyForStatement){
        if (pyForStatement instanceof PyForStatementImpl) {
            PyForStatementImpl forStatement = (PyForStatementImpl) pyForStatement;
            if (forStatement.isAsync()) {
                this.addTerminal(new TokenNode("async"));
                this.visitForStatement(forStatement);
            }
            else{
                visitForStatement(forStatement);
            }
            if (forStatement.getElsePart() != null){
                this.addTerminal(new TokenNode("else"));
                this.visitPyStatementList(forStatement.getElsePart().getStatementList());
            }
        }
    }
    void visitForStatement(PyForStatementImpl forStatement){
        this.visitFor(forStatement.getForPart());
    }

    void visitFor(PsiElement forLoop){
        if (forLoop instanceof PyForPartImpl) {
            PyForPartImpl forPart = (PyForPartImpl) forLoop;
            this.addTerminal(new TokenNode("for"));
            this.visit(forPart.getTarget());
            this.addTerminal(new TokenNode("in"));
            this.visit(forPart.getSource());
            this.addEdge(forPart.getTarget(), forPart.getSource(), EdgeType.COMPUTED_FROM);
            this.visit(forPart.getStatementList());
        }
    }

    // End Region: Control Flow

    void visitPyExpressionStatement(PsiElement pyExpressionNode){
        PyExpressionStatementImpl pyExpressionStatement = (PyExpressionStatementImpl) pyExpressionNode;
        this.visit(pyExpressionStatement.getExpression());
    }

    void visitPyCallExpression(PsiElement callExpressionNode){
        if (callExpressionNode instanceof PyCallExpressionImpl){
            PyCallExpressionImpl callExpression = (PyCallExpressionImpl) callExpressionNode;
            this.visit(callExpression.getCallee());
            this.addTerminal(new TokenNode("("));
            int addedArgs = 0;
            for (PsiElement argument : callExpression.getArguments()) {
                this.visit(argument);
                addedArgs += 1;
                if (addedArgs < callExpression.getArguments().length){
                    this.addTerminal(new TokenNode(","));
                }
            }
            //TODO: keywords?
            this.addTerminal(new TokenNode(")"));
        }
    }





    // Region: Data Structure Constructors
    void visitPyDictLiteralExpression(PsiElement dictNode){
        if (dictNode instanceof PyDictLiteralExpressionImpl){
            PyDictLiteralExpressionImpl dictExpression = (PyDictLiteralExpressionImpl) dictNode;
            this.addTerminal(new TokenNode("{"));
            int idx = 0;
            for (PyKeyValueExpression keyValueExpression : dictExpression.getElements()){
                if (keyValueExpression.getKey() instanceof PyNoneLiteralExpressionImpl){
                    this.addTerminal(new TokenNode("None"));
                }
                else{
                    this.visit(keyValueExpression.getKey());
                }
                this.addTerminal(new TokenNode(":"));
                this.visit(keyValueExpression.getValue());
                if (idx < dictNode.getChildren().length - 1){
                    this.addTerminal(new TokenNode(","));
                }
            }
            this.addTerminal(new TokenNode("}"));
        }
    }

    void visitPyFormattedStringElement(PsiElement formattedStringNode){
        if (formattedStringNode instanceof  PyFormattedStringElementImpl){
            PyFormattedStringElement formattedStringElement = (PyFormattedStringElement) formattedStringNode;
            this.addTerminal(new TokenNode("f\""));
            //skip the firstChild (= f"), then visit each of its next siblings
            //this is a workaround
            PsiElement currentSibling = formattedStringElement.getFirstChild().getNextSibling();
            while (currentSibling != null){
                if (currentSibling instanceof LeafPsiElement){
                    this.visitPyStringLiteralExpression(currentSibling);
                }
                else{
                    this.visit(currentSibling);
                }
                currentSibling = currentSibling.getNextSibling();
            }

        }
    }

    void visitPyFStringFragment(PsiElement FStringFragmentNode){
        for (PsiElement formatFragment : FStringFragmentNode.getChildren()){
            visit(formatFragment);
        }
    }

    void visitPyTupleExpression(PsiElement tupleExpressionNode){
        this.visitSequenceDataStruct(tupleExpressionNode, "(", ")");
    }

    void visitPySetLiteralExpression(PsiElement setExpressionNode){
        this.visitSequenceDataStruct(setExpressionNode, "{", "}");

    }

    void visitPyListLiteralExpression(PsiElement listExpressionNode){
        this.visitSequenceDataStruct(listExpressionNode, "[", "]");

    }

    void visitPyParenthesizedExpression(PsiElement parenthesizedExpressionNode){
        for(PsiElement element: parenthesizedExpressionNode.getChildren()){
            this.visit(element);
        }
    }

    void visitSequenceDataStruct(PsiElement dataStructNode, String openBrace, String closeBrace){
        this.addTerminal(new TokenNode(openBrace));
        for (PsiElement element : dataStructNode.getChildren()){
            this.visit(element);
            this.addTerminal(new TokenNode(","));
        }
        this.addTerminal(new TokenNode(closeBrace));
    }

    // End Region


    // Region: literals and constructor-likes
    void visitPyStringLiteralExpression(PsiElement pyStringLiteralNode){
        if (pyStringLiteralNode instanceof  PyStringLiteralExpressionImpl){
            PyStringLiteralExpressionImpl string = (PyStringLiteralExpressionImpl) pyStringLiteralNode;
            if (string.getChildren().length == 0) {
                this.addTerminal(new TokenNode("\"" + string.getStringValue() + "\""));
            }
            // else for formatted strings
            else{
                for (PsiElement stringElement : string.getChildren()) {
                    this.visit(stringElement);
                }
            }
        }
        else{
            // only to match string inverted commas for formatted strings
            if (pyStringLiteralNode.getContext() != null &&
                    pyStringLiteralNode.isEquivalentTo(pyStringLiteralNode.getContext().getLastChild())) {
                this.addTerminal(new TokenNode("\"" + "\"" + "\""));
            }
            else{
                this.addTerminal(new TokenNode("\"" + pyStringLiteralNode.getText() + "\""));
            }
        }
    }

    void visitPyNumericLiteralExpression(PsiElement node){
        this.addTerminal(new TokenNode(node.getText()));
    }


    // End Region

    boolean isAttribute(PsiElement node){
        if (node instanceof PyTargetExpressionImpl || node instanceof PyReferenceExpressionImpl){
            return ((PyQualifiedExpression) node).isQualified();
        }
        return false;
    }

    // Region: ClassDef

    void visitPyClass(PsiElement classDefNode){
        if (classDefNode instanceof PyClassImpl){
            PyClassImpl classNode = (PyClassImpl) classDefNode;

            //TODO: inheritance (needs type_lattice_generator.py code)

            if (classNode.getDecoratorList() != null){
                for (PyDecorator decorator: classNode.getDecoratorList().getDecorators()){
                    this.addTerminal(new TokenNode("@"));
                    this.visit(decorator);
                }
            }

            this.addTerminal(new TokenNode("class"));
            this.addTerminal(new TokenNode(classNode.getName(), classNode.getTextOffset()));
            PyExpression[] superClassExpressions = classNode.getSuperClassExpressions();
            if (superClassExpressions.length > 0){
                this.addTerminal(new TokenNode("("));
                int i = 0;
                for (PsiElement superClass : superClassExpressions){
                    this.visit(superClass);
                    if (i < superClassExpressions.length - 1 ){
                        this.addTerminal(new TokenNode(","));
                    }
                }
                this.addTerminal(new TokenNode(")"));
            }

            this.makeNewScope();
            //add all instance attributes to current extracted symbols
            for (PyTargetExpression attribute : classNode.getInstanceAttributes()){
                this.instanceAttributes.put(attribute.getText(), new Pair<>(attribute, new Symbol(attribute)));
            }
            try{
                this.visitPyStatementList(classNode.getStatementList());
            }finally {
                this.exitScope();
                this.instanceAttributes.clear();
            }
        }
    }
}
