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
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes.TypeAnnotationNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.intellij.openapi.util.Pair;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.*;
import java.lang.reflect.Type;
import java.util.*;

public class GraphGenerator {
    LinkedHashMap<String, Object> graph = new LinkedHashMap<String, Object>();
    ArrayList<Object> idToNode = new ArrayList<>();
    ArrayList<HashMap<String, Pair<PsiElement, Symbol>>> scopes = new ArrayList<>();
    //ArrayList<HashMap<String, Pair<PsiElement, Symbol>>> allScopes = new ArrayList<>();
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
    HashMap<String, Pair<PsiElement, Symbol>> attributes = new HashMap<>();
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

    HashMap<PyElementType, String> UNARYOPS = new HashMap<PyElementType, String>(){{
            put(PyTokenTypes.NOT_KEYWORD, "not");
            put(PyTokenTypes.TILDE, "~");
            put(PyTokenTypes.PLUS, "+");
            put(PyTokenTypes.MINUS, "-");
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
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                ((LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges"));

        graphEdges.put(EdgeType.CHILD, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.NEXT, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.COMPUTED_FROM, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.RETURNS_TO, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.OCCURRENCE_OF, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.SUBTOKEN_OF, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.LAST_LEXICAL_USE, new LinkedHashMap<String, ArrayList<Integer>>());
        graphEdges.put(EdgeType.NEXT_USE, new LinkedHashMap<String, ArrayList<Integer>>());
        this.graph.put("token-sequence", new ArrayList<Integer>());
        this.graph.put("supernodes", new LinkedHashMap<String, LinkedHashMap<String, Object>>());
    }

    public String toString(){
        return graph.toString();
    }

    /**
     * Creates a new HashMap to store the elements in current scope
     */
    void makeNewScope(){
        HashMap<String, Pair<PsiElement, Symbol>> newScope = new HashMap<>();
        this.scopes.add(newScope);
        this.currentExtractedSymbols = newScope;

    }

    /**
     * Exits the current scope
     */
    void exitScope(){
        this.currentExtractedSymbols = this.scopes.get(this.scopes.size()-2);
        this.scopes.remove(this.scopes.size()-1);
    }

    /**
     * Main visit method which generates the specific visit method name for each Psi Element
     * @param node
     */

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
     * Adds a terminal node to the graph
     * @param tokenNode Takes a TokenNode item to add to the graph
     */
    void addTerminal(TokenNode tokenNode){
        this.addEdge(this.currentParentNode, tokenNode, EdgeType.CHILD);
        if (this.prevTokenNode != null){
            this.addEdge(this.prevTokenNode, tokenNode, EdgeType.NEXT);
        }
        this.backboneSequence.add(tokenNode);
        this.prevTokenNode = tokenNode;

    }

    /**
     * Visits PyFile then calls generateGraph()
     * @param pyFile The PsiElement of the root file
     */
    void visitPyFile(PsiElement pyFile) {
        PyFileImpl file = (PyFileImpl) pyFile;
        for (PsiElement element : file.getChildren()) {
            this.visit(element);
        }
        String filename = pyFile.getContainingFile().getContainingDirectory()
                + pyFile.getContainingFile().getName();
        this.graph.put("filename", filename);
        System.out.println(this.generateGraph());
    }

    /**
     * Generates the graph of the code file and returns a String Json of the graph
     */
    String generateGraph(){
        this.addSubtokenOfEdges();

        //add supernodes to graph
        for (Map.Entry item : this.variableLikeSymbols.entrySet()) {
            if (parseSymbolInfo((SymbolInformation) item.getValue()) != null) {
                ((LinkedHashMap<String, LinkedHashMap<String, Object>>) this.graph.get("supernodes"))
                        .put(this.nodeToId.get(item.getKey()).toString(),
                                parseSymbolInfo((SymbolInformation) item.getValue()));
            }
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

        return (this.graphToJson());
    }

    /**
     * Parses the symbol information of each supernode to add to the graph
     * @param sInfo The SymbolInformation object of the symbol
     * @return A HashMap of the name, annotation, location and type of the symbol
     */

    LinkedHashMap<String, Object> parseSymbolInfo(SymbolInformation sInfo){
        String annotation = null;
        try {
            Integer firstAnnotatableLocation = Collections.min(sInfo.annotatableLocations.keySet());
            return new LinkedHashMap<String, Object>(){{
                put("name", sInfo.name);
                put("annotation", annotation);
                put("location", firstAnnotatableLocation);
                put("type", sInfo.symbolType);
            }};
        }
        catch (NoSuchElementException ignored){

        }
        return null;
    }

    /**
     * Visits PyAssignmentStatement
     * @param node The PsiElement
     */
    void visitPyAssignmentStatement(PsiElement node) {
        if(node instanceof PyAssignmentStatementImpl){
            PyAssignmentStatementImpl assignNode = (PyAssignmentStatementImpl) node;
            Iterator<PyExpression> iterator = Arrays.asList((assignNode.getRawTargets())).iterator();
            int i = 0;
            while(iterator.hasNext()){
                PsiElement target = iterator.next();
                if (isAttribute(node)){
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
                i += 1;
            }
            this.addTerminal(new TokenNode("="));
            this.visit(assignNode.getAssignedValue());
        }

          //TODO add for i > 1 targets

    }

    /**
     * Visits PyTargetExpression. Collect unseen non-self and simple target symbols
     * Currently only supports targets of form X and X.Y
     * @param target The PsiElement of the target
     */

    void visitPyTargetExpression(PsiElement target){
        PyTargetExpressionImpl targetExpression = (PyTargetExpressionImpl) target;

        //Do not collect symbols for instance variables with prefix "self"
        //these are collected in the beginning of each class
        if (isAttribute(targetExpression)) {
            if (targetExpression.getQualifier() != null &&  targetExpression.getQualifier().getText().equals("self")) {
                this.visitVariableLike(targetExpression, targetExpression.getFirstChild().getTextOffset(), true, null);
            }

            //Collect if attribute unseen
            else {
                if (!this.attributes.containsKey(targetExpression.getText())) {
                    this.attributes.put(targetExpression.getText(), new Pair<>(targetExpression, new Symbol(targetExpression)));
                }
                this.visitVariableLike(targetExpression, targetExpression.getFirstChild().getTextOffset(), true, null);
            }
        }
        // Collect if unseen
        else {
            if (!this.currentExtractedSymbols.containsKey(targetExpression.getReferencedName())) {
                this.currentExtractedSymbols.put(targetExpression.getReferencedName(), new Pair<>(targetExpression, new Symbol(targetExpression)));
            }
            this.visitVariableLike(targetExpression, targetExpression.getTextOffset(), true, null);
        }

    }

    /**
     * Visits PyReferenceExpression
     * Collects unseen non-self attributes and symbols
     * Currently supports references of form X and X.Y
     * @param referenceExpressionNode The PsiElement
     */
    void visitPyReferenceExpression(PsiElement referenceExpressionNode) {
        if (referenceExpressionNode instanceof PyReferenceExpressionImpl) {
            PyReferenceExpressionImpl referenceExpression = (PyReferenceExpressionImpl) referenceExpressionNode;

            //When a variable x is defined in the global scope, if it is then referred to within a function
            //e.g "if x == 1", Typilus does not recognize this as a reference to the outer x
            //Maybe it should?

            //do not collect symbols for instance variables with prefix "self"
            //these are collected in the beginning of each class
            if (isAttribute(referenceExpression)) {
                if (referenceExpression.getQualifier() != null && referenceExpression.getQualifier().getText().equals("self")) {
                    this.visitVariableLike(referenceExpression, referenceExpression.getFirstChild().getTextOffset(), false, null);
                } else {
                    if (!this.attributes.containsKey(referenceExpression.getText())) {
                        this.attributes.put(referenceExpression.getText(), new Pair<>(referenceExpression, new Symbol(referenceExpression)));
                    }
                    this.visitVariableLike(referenceExpression, referenceExpression.getFirstChild().getTextOffset(), false, null);
                }
            } else {
                if (!this.currentExtractedSymbols.containsKey(referenceExpression.getReferencedName())) {
                    this.currentExtractedSymbols.put(referenceExpression.getReferencedName(), new Pair<>(referenceExpression, new Symbol(referenceExpression)));
                }
                this.visitVariableLike(referenceExpression, referenceExpression.getTextOffset(), false, null);
            }
        }
    }

    /**
     * Visits PyStatementList
     * @param node
     */
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

    /**
     * Gives a representation of a node to be added to the graph
     * @param node The node can be of types: String, TokenNode, PsiElement or Symbol
     * @return The string representation of the node
     * @throws ClassNotFoundException When node is of another type of object
     */
    String nodeToLabel(Object node) throws ClassNotFoundException {
        if (node instanceof String){
            return ((String) node).replace("\n", "");
        }
        else if (node instanceof TokenNode){
            return ((TokenNode) node).token.replace("\n", "");
        }
        else if (node instanceof PsiElement){
            return ((PsiElement) node).toString();
        }
        else if (node instanceof Symbol){
            return ((Symbol) node).getSymbol().replace("\n", "");
        }
        else {
            throw new ClassNotFoundException();
        }
    }

    /**
     * Adds an index to the given node to add to the graph
     * @param node TokenNode node
     * @return Returns the integer index of the node
     */
    public int nodeID(TokenNode node){
        if (this.nodeToId.get(node) == null){
            int idx = this.nodeToId.size();
            assert (this.idToNode.size() == this.nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
        }
        return this.nodeToId.get(node);
    }

    /**
     * Adds an index to the given node to add to the graph
     * @param node PsiElement node
     * @return Returns the integer index of the node
     */
    int nodeID(PsiElement node){
        if (this.nodeToId.get(node) == null){
            int idx = this.nodeToId.size();
            assert (idToNode.size() == nodeToId.size());
            this.nodeToId.put(node, idx);
            this.idToNode.add(node);
            return idx;
        }
        return this.nodeToId.get(node);
    }

    /**
     * Adds an index to the given node to add to the graph
     * @param node Symbol node
     * @return Returns the integer index of the node
     */
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

    /**
     * Adds SUBTOKEN_OF edges to the graph. Splits the identifier in parts and finds its sub-tokens
     */
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
            List<String> identifierParts = splitIdentifierIntoParts(node.token);
            identifierParts.removeAll(Arrays.asList("",null));

            for (String subtoken : identifierParts){
                TokenNode subtokenDummyNode = subtokenNodes.get(subtoken);
                if (subtokenDummyNode == null){
                    subtokenDummyNode = new TokenNode(subtoken);

                    subtokenNodes.put(subtoken, subtokenDummyNode);
                }
                this.addEdge(subtokenDummyNode, node, EdgeType.SUBTOKEN_OF);

            }
        }
    }

    /**
     * Splits a snake case string and then for each substring, splits camel case
     * Needed for the SUBTOKEN_OF edge in the graph
     * @param identifier Identifier string to split
     * @return A combined list of all the substrings
     */

    List<String> splitIdentifierIntoParts(String identifier){
        List<String> splitSnake = new ArrayList<String>(Arrays.asList(identifier.split("_")));
        List<String> results = new ArrayList<>();
        for (String s : splitSnake){
            results.addAll(new ArrayList<String>(Arrays.asList(s.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))));
        }
        return results;
    }


    /**
     * Checks if the given Object node is an identifier
     * @param node Object to check for. This can be of types: TokenNode, PsiElement or Symbol
     * @return Returns true if node belongs to identifier, false otherwise
     */
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


    /**
     * Adds an edge from a PsiElement to another PsiElement
     * @param fromNode PsiElement to add edge from
     * @param toNode PsiElement to add edge to
     * @param edgeType The EdgeType
     */
    void addEdge(PsiElement fromNode, PsiElement toNode, EdgeType edgeType) {
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                (LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges");

        if (graphEdges.get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            graphEdges.get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            graphEdges.get(edgeType).put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
    }

    /**
     * Adds an edge from a PsiElement to a TokenNode
     * @param fromNode PsiElement to add edge from
     * @param toNode TokenNode to add edge to
     * @param edgeType The EdgeType
     */
    void addEdge(PsiElement fromNode, TokenNode toNode, EdgeType edgeType) {
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                (LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges");

        if (graphEdges.get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            graphEdges.get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            graphEdges.get(edgeType).put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
    }

    /**
     * Adds an edge from a TokenNode to another TokenNode
     * @param fromNode TokenNode to add edge from
     * @param toNode TokenNode to add edge to
     * @param edgeType The EdgeType
     */
    void addEdge(TokenNode fromNode, TokenNode toNode, EdgeType edgeType) {
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                (LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges");

        if (graphEdges.get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            graphEdges.get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            graphEdges.get(edgeType).put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
    }

    /**
     * Adds an edge from a TokenNode to a Symbol
     * @param fromNode TokenNode to add edge from
     * @param toNode Symbol to add edge to
     * @param edgeType The EdgeType
     */
    void addEdge(TokenNode fromNode, Symbol toNode, EdgeType edgeType) {
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                (LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges");

        if (graphEdges.get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            graphEdges.get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            graphEdges.get(edgeType).put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
    }

    /**
     * Adds an edge from a TokenNode to a PsiElement
     * @param fromNode TokenNode to add edge from
     * @param toNode PsiElement to add edge to
     * @param edgeType The EdgeType
     */
    void addEdge(TokenNode fromNode, PsiElement toNode, EdgeType edgeType) {
        int fromNodeIdx = this.nodeID(fromNode);
        int toNodeIdx = this.nodeID(toNode);
        LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>> graphEdges =
                (LinkedHashMap<EdgeType, LinkedHashMap<String, ArrayList<Integer>>>) this.graph.get("edges");

        if (graphEdges.get(edgeType).containsKey(Integer.toString(fromNodeIdx))) {
            graphEdges.get(edgeType).get(Integer.toString(fromNodeIdx)).add(toNodeIdx);
        } else {
            graphEdges.get(edgeType).put(Integer.toString(fromNodeIdx), new ArrayList<Integer>(Arrays.asList(toNodeIdx)));
        }
    }



    //Region: PyFunctions

    /**
     * Visits PyFunction
     * @param node The PsiElement of the PyFunction
     */
    void visitPyFunction(PsiElement node){
        if (node instanceof PyFunctionImpl){
            PyFunctionImpl funcNode = (PyFunctionImpl) node;
            if (funcNode.getDecoratorList() != null) {
                for (PyDecorator decorator : funcNode.getDecoratorList().getDecorators()) {
                    this.addTerminal(new TokenNode("@"));
                    this.visit(decorator);
                }
            }
            if (funcNode.isAsync()){
                this.addTerminal(new TokenNode("async"));
            }
            this.addTerminal(new TokenNode("def"));

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

    /**
     * Visits PyNamedParameters. Currently does not parse type annotations of parameters
     * @param node The PsiElement of the PyNamedParameter
     */
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

    /**
     * Visits PyReturnStatement
     * @param returnNode The PsiElement of the PyReturnStatement
     */
    void visitPyReturnStatement(PsiElement returnNode){
        this.addEdge(returnNode, this.returnScope, EdgeType.RETURNS_TO);
        this.addTerminal(new TokenNode("return"));
        for (PsiElement returnElement : returnNode.getChildren()){
            this.visit(returnElement);
        }
    }

    /**
     * Visits PyLambdaExpression
     * @param lambdaNode The PsiElement of the PyLambdaExpression
     */
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

    /**
     * Vistis PyYieldExpression. Supports both "yield" and "yield from"
     * @param node The PsiElement of the PyYieldExpression
     */
    void visitPyYieldExpression(PsiElement node) {
        if (node instanceof PyYieldExpressionImpl) {
            this.addEdge(node, this.returnScope, EdgeType.RETURNS_TO);
            this.addTerminal(new TokenNode("yield"));
            //for "yield from"
            if (node.getFirstChild().getNextSibling().getNextSibling().getText().equals("from")){
                this.addTerminal(new TokenNode("from"));
            }
            if (((PyYieldExpressionImpl) node).getExpression() != null){
                this.visit(((PyYieldExpressionImpl) node).getExpression());
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

    /**
     * Visits PyForStatement. Visits else part directly as a PyStatementList
     * @param pyForStatement The PsiElement of the PyForStatement
     */
    void visitPyForStatement(PsiElement pyForStatement){
        if (pyForStatement instanceof PyForStatementImpl) {
            PyForStatementImpl forStatement = (PyForStatementImpl) pyForStatement;
            if (forStatement.isAsync()) {
                this.addTerminal(new TokenNode("async"));
            }
            visitFor(forStatement.getForPart());
            if (forStatement.getElsePart() != null){
                this.addTerminal(new TokenNode("else"));
                this.visitPyStatementList(forStatement.getElsePart().getStatementList());
            }
        }
    }

    /**
     * Helper method for PyForStatement
     * @param forLoop The PsiElement of the PyForStatement
     */
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

    /**
     * Visits PyIfStatment. PSI has a PyElsePart but here else clauses are visited directly.
     * @param node The PsiElement of the PyIfStatement
     */
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


    /**
     * Visits PyIfPartIf. Visits the if part of an if-elif-else statement.
     * Does not visitPyStatementList to match the Typilus graph more
     * @param node The PsiElement of the PyIfPartIf
     */
    void visitPyIfPartIf(PsiElement node){
        this.addTerminal(new TokenNode("if"));
        for (PsiElement child : node.getChildren()){
            this.visit(child);
        }
    }

    /**
     * Visits PyRaiseStatement. Assumes that PyRaiseStatement has exactly two children
     * First child is the raise part
     * Last child is the from part
     * @param node The PsiElement of the PyRaiseStatement
     */
    void visitPyRaiseStatement(PsiElement node){
        if (node instanceof PyRaiseStatementImpl){
            PyRaiseStatement raiseStatement = (PyRaiseStatement) node;
            this.addEdge(node, this.returnScope, EdgeType.RETURNS_TO);
            this.addTerminal(new TokenNode("raise"));
            this.visit(node.getFirstChild());
            if (node.getChildren().length > 1){
                this.addTerminal(new TokenNode("from"));
                this.visit(raiseStatement.getLastChild());
            }
        }
    }

    /**
     * Visits PyTryExceptStatement. First visits the try part then the except then the finally
     * @param node The PsiElement of the PyTryExceptStatement
     */
    void visitPyTryExceptStatement(PsiElement node){
        if (node instanceof PyTryExceptStatementImpl){
            PyTryExceptStatement tryExceptStatement = (PyTryExceptStatement) node;
            this.addTerminal(new TokenNode("try"));
            this.visitPyStatementList(tryExceptStatement.getTryPart().getStatementList());
            int numberOfHandlers = 0;
            for (PyExceptPart exceptPart: tryExceptStatement.getExceptParts()){
                this.visit(exceptPart);
                if (numberOfHandlers > 0){
                    this.addEdge(tryExceptStatement.getExceptParts()[numberOfHandlers - 1], exceptPart, EdgeType.NEXT);
                }
                numberOfHandlers += 1;
            }
            if (tryExceptStatement.getElsePart() != null){
                this.addTerminal(new TokenNode("else"));
                this.visitPyStatementList(tryExceptStatement.getElsePart());
            }
            if (tryExceptStatement.getFinallyPart() != null){
                this.addTerminal(new TokenNode("finally"));
                this.visitPyStatementList(tryExceptStatement.getFinallyPart());
            }
        }
    }

    /**
     * Visits PyExceptPart
     * @param node The PsiElement of the PyExceptPart
     */
    void visitPyExceptPart(PsiElement node){
        if (node instanceof PyExceptPartImpl){
            PyExceptPart exceptPart = (PyExceptPart) node;
            this.addTerminal(new TokenNode("except"));
            this.visit(exceptPart.getExceptClass());
            this.visitPyStatementList(exceptPart.getStatementList());
        }
    }

    /**
     * Visits PyWhileStatement. First visits the while part then the else part, if it exists
     * @param node The PsiElement of the PyWhileStatement
     */
    void visitPyWhileStatement(PsiElement node){
        if (node instanceof PyWhileStatement){
            PyWhileStatement whileStatement = (PyWhileStatement) node;
            this.addTerminal(new TokenNode("while"));
            this.visit(whileStatement.getWhilePart().getCondition());
            this.visitPyStatementList(whileStatement.getWhilePart().getStatementList());
            if (whileStatement.getElsePart() != null){
                this.addTerminal(new TokenNode("else"));
                this.visitPyStatementList(whileStatement.getElsePart().getStatementList());
            }
        }
    }

    /**
     * Visits PyWithStatement. Visits each PyWithItem within the statement
     * @param node The PsiElement of the PyWithStatement
     */
    void visitPyWithStatement(PsiElement node){
        if (node instanceof PyWithStatementImpl){
            PyWithStatement withStatement = (PyWithStatement) node;
            if (withStatement.isAsync()){
                this.addTerminal(new TokenNode("async"));
            }

            this.addTerminal(new TokenNode("with"));
            int items = 0;
            for (PyWithItem item : withStatement.getWithItems()){
                this.visit(item);
                if (items <withStatement.getWithItems().length - 1){
                    this.addTerminal(new TokenNode(","));
                }
            }
            this.visitPyStatementList(withStatement.getStatementList());
        }
    }

    /**
     * Visits PyWithItem. First visits the expression then the target
     * @param node The PsiElement of the PyWithItem
     */
    void visitPyWithItem(PsiElement node){
        if (node instanceof PyWithItemImpl){
            PyWithItem withItem = (PyWithItem) node;
            this.visit(withItem.getExpression());
            if (withItem.getTarget() != null){
                this.addTerminal(new TokenNode("as"));
                this.visit(withItem.getTarget());
            }
        }
    }

    // End Region: Control Flow

    // Region: Comprehensions
    // TODO: comprehensions
    // End Region: Comprehensions

    /**
     * Visits PyExpressionStatement then visits the expression from the statement
     * @param pyExpressionNode The PsiElement of the PyExpressionStatement
     */
    void visitPyExpressionStatement(PsiElement pyExpressionNode){
        PyExpressionStatementImpl pyExpressionStatement = (PyExpressionStatementImpl) pyExpressionNode;
        this.visit(pyExpressionStatement.getExpression());
    }

    /**
     * Visits PyCallExpression by visitng the callee then the arguments
     * @param callExpressionNode The PsiElement of the PyCallExpression
     */
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

    //Region: Simple Expressions

    /**
     * Assert is assumed to have two PSI arguments:
     *      - the first being the test/argument
     *      - the second being the error
     * @param node
     */
    void visitPyAssertStatement(PsiElement node){
        if (node instanceof PyAssertStatementImpl){
            PyAssertStatement assertStatement = (PyAssertStatement) node;
            this.addTerminal(new TokenNode("assert"));
            this.visit(assertStatement.getArguments()[0]);
            if (assertStatement.getArguments().length > 1){
                this.addTerminal(new TokenNode(","));
                this.visit(assertStatement.getArguments()[1]);
            }
        }
    }

    void visitPyBoolLiteralExpression(PsiElement node){
        this.addTerminal(new TokenNode(node.getText()));
    }

    /**
     * Visits PyBinaryExpression first visiting the left expression then the right
     * @param node The PsiElement of the PyBinaryExpression
     */
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

    /**
     * Visits PyDelStatement
     * @param node The PsiElement of the PyDelStatement
     */
    void visitPyDelStatement(PsiElement node){
        if (node instanceof PyDelStatementImpl){
            PyDelStatement delStatement = (PyDelStatement) node;
            this.addTerminal(new TokenNode("del"));
            int targets = 0;
            for (PyExpression target : delStatement.getTargets()){
                this.visit(target);
                if (targets < delStatement.getTargets().length - 1){
                    this.addTerminal(new TokenNode(","));
                }
            }
        }
    }

    /**
     * Visits PyPrefixExpression, the prefix expression of a Unary Operation
     * This is the same as Typilus' visit_UnaryOp
     * @param node The PsiElement of the PyPrefixExpression
     */
    void visitPyPrefixExpression(PsiElement node){
        if (node instanceof PyPrefixExpressionImpl) {
            PyPrefixExpressionImpl prefixExpression = (PyPrefixExpressionImpl) node;
            String operator = this.UNARYOPS.get(prefixExpression.getOperator());
            this.addTerminal(new TokenNode(operator));
            this.visit(prefixExpression.getOperand());
        }
    }

    /**
     * Visits PySliceExpression
     * PSI cannot detected multiple slices in one slice expression
     * This functions works around by looking at siblings of the first slice item
     * @param node The PsiElement of the PySliceExpression
     */
    void visitPySliceExpression(PsiElement node){
        if (node instanceof PySliceExpressionImpl){
            PySliceExpression sliceExpression = (PySliceExpression) node;
            this.visit(sliceExpression.getOperand());
            this.addTerminal(new TokenNode("["));
            this.visit(sliceExpression.getSliceItem());
            PsiElement sibling = sliceExpression.getSliceItem();
            while (sibling.getNextSibling() != null){
                if (sibling.getNextSibling() instanceof PySliceItem) {
                    this.visit(sibling.getNextSibling());
                }
                sibling = sibling.getNextSibling();
            }
            this.addTerminal(new TokenNode("]"));
        }
    }

    /**
     * Visits PySliceItem
     * @param node The PsiElement of the PySliceItem
     */

    void visitPySliceItem(PsiElement node){
        if (node instanceof PySliceItemImpl){
            PySliceItem sliceItem = (PySliceItem) node;
            this.addTerminal(new TokenNode("["));
            if (sliceItem.getLowerBound() != null){
                this.visit(sliceItem.getLowerBound());
            }
            if (sliceItem.getUpperBound() != null){
                this.visit(sliceItem.getUpperBound());
            }
            if (sliceItem.getStride() != null){
                this.visit(sliceItem.getStride());
            }
            this.addTerminal(new TokenNode("]"));
        }
    }

    /**
     * Visits PySubscriptionExpression
     * PSI does not have Index as in Typilus
     * PSI only sees the indexExpression of a subscription as a general PsiElement e.g. PyReferenceExpression
     * @param node The PsiElement of the PySubscriptionExpression
     */
    void visitPySubscriptionExpression(PsiElement node){
        if (node instanceof PySubscriptionExpressionImpl){
            PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression) node;
            this.visit(subscriptionExpression.getOperand());
            this.addTerminal(new TokenNode("["));
            this.visit(subscriptionExpression.getIndexExpression());
            this.addTerminal(new TokenNode("]"));
        }
    }

    //TODO: Starred expressions
    //TODO: visit aliases, requires parsing type annotations

    void visitPyGlobalStatement(PsiElement node) {
        if (node instanceof PyGlobalStatementImpl) {
            this.addTerminal(new TokenNode("global"));
            for (PyTargetExpression targetExpression : ((PyGlobalStatementImpl) node).getGlobals()) {
                if (!this.currentExtractedSymbols.containsKey(targetExpression.getReferencedName())) {
                    this.currentExtractedSymbols.put(targetExpression.getReferencedName(), new Pair<>(targetExpression, new Symbol(targetExpression)));
                }
                this.visitVariableLike(targetExpression, targetExpression.getTextOffset(), false, null);
            }
        }
    }

    void visitPyPassStatement(PsiElement node){
        this.addTerminal(new TokenNode("pass"));
    }

    void visitPyNonlocalStatement(PsiElement node){
        if (node instanceof PyNonlocalStatement) {
            this.addTerminal(new TokenNode("nonlocal"));
            for (PyTargetExpression targetExpression : ((PyNonlocalStatement) node).getVariables()) {
                if (!this.currentExtractedSymbols.containsKey(targetExpression.getReferencedName())) {
                    this.currentExtractedSymbols.put(targetExpression.getReferencedName(), new Pair<>(targetExpression, new Symbol(targetExpression)));
                }
                this.visitVariableLike(targetExpression, targetExpression.getTextOffset(), false, null);
            }
        }
    }


    //End Region: Control Flow


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

    // End Region: Data Structure Constructors


    // Region: Literals and constructor-likes
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

    // End Region: Literals and constructor-likes



    // Region: Class Def

    /**
     * Visits PyClass node
     * @param classDefNode The PsiElement of the PyClass node
     */
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

            //collect all instance attriubutes for class then clear once the class is exited
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

    //End Region: Class Def


    /**
     * Visits a variable-like PsiElement e.g. variable names and function names
     * @param node The PsiElement corresponding to the variable-like item
     * @param startOffset The startOffset location of the item
     * @param canAnnotateHere Can the item be annotated?
     * @param typeAnnotation The type annotation object of the item, if any
     */
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
        }

    }


    /**
     * Gets the symbol for the name of a variable-like node
     * Currently does not support special underscore variables
     * @param node The PsiElement
     * @param startOffset The location of the element
     * @return A HashMap of type {"node"=TokenNode, "name"=String, "symbol"=Symbol, "symbolType"=String}
     */
    HashMap<String, Object> getSymbolForName(PsiElement node, int startOffset) {
        HashMap<String, Object> results = new HashMap<>();
        Symbol symbol = null;
        if (!isAttribute(node)) {
            TokenNode newNode = new TokenNode(node.getText(), startOffset);
            this.addTerminal(newNode);

            //TODO special underscored items
            results.put("node", newNode);

            symbol = getScopeSymbol(node);
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
                this.nodeID(newNode);
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
                results.put("symbolType", "class-or-function");
            } else if (symbolsPsiType instanceof PyNamedParameterImpl){
                results.put("symbolType", "parameter");
            }
            else if (symbolsPsiType instanceof PyImportStatementImpl){
                results.put("symbolType", "imported");
            }
        }
        return results;
    }

    /**
     * Gets the Symbol from the current scope, instance attributes, or non-self attributes
     * @param node The PsiElement to get the Symbol for
     * @return A Symbol of the PsiElement
     */
    Symbol getScopeSymbol(PsiElement node){
        if (isAttribute(node)){
            //if instance attribute then look at map of instance attributes of class
            if (node.getFirstChild().getText().equals("self")) {
                for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.instanceAttributes.entrySet()) {
                    if (entry.getKey().equals(node.getText())) {
                        return entry.getValue().getSecond();
                    }
                }
            }
            else {
                for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.attributes.entrySet()) {
                    if (entry.getKey().equals(node.getText())) {
                        return entry.getValue().getSecond();
                    }
                }
            }
        }
        else {
            //backward traverse the scopes to get the most recent Symbol
            for (int i = this.scopes.size(); i > 0; i--) {
                PsiReference reference = node.getReference();
                // resolve a variable reference, or find function, or find parameters
                if (reference != null ||
                        node.getParent().getClass() == PyFunctionImpl.class ||
                        node.getClass() == PyNamedParameterImpl.class
                ) {
                    for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.scopes.get(i - 1).entrySet()) {
                        if (entry.getKey().equals(node.getText())) {
                            return entry.getValue().getSecond(); //returns the corresponding Symbol
                        }
                    }
                }

            }
        }

        //System.out.println("printing null node");
        //System.out.println(node.getText());
        return null;
    }

    /**
     * Checks if node has the form X.Y, thus checking if it is an attribute
     * @param node The PsiElement to check
     * @return True if it is an attribute, false otherwise
     */
    boolean isAttribute(PsiElement node){
        if (node instanceof PyTargetExpressionImpl || node instanceof PyReferenceExpressionImpl){
            return ((PyQualifiedExpression) node).isQualified();
        }
        return false;
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
}
