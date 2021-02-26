package com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiElementFilter;
import com.jetbrains.intellijTypilus.data_preparation.scripts.graph_generator.typeparsing.nodes.TypeAnnotationNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;



import java.util.*;

public class GraphGenerator {
    LinkedHashMap<String, Object> graph = new LinkedHashMap<String, Object>();
    //LinkedHashMap<EdgeType, LinkedHashMap<Integer, Set<Integer>>> edges;
    ArrayList<Object> idToNode = new ArrayList<>();
    ArrayList<HashMap<String, Pair<PsiElement, Symbol>>> scopes = new ArrayList<>();
    PsiElement returnScope = null;
    TokenNode prevTokenNode = null;
    ArrayList<TokenNode> backboneSequence = new ArrayList<>();
    public LinkedHashMap<Object, Integer> nodeToId = new LinkedHashMap<>();
    public PsiFile psiFile = null;
    public PsiElement currentParentNode = null;
    HashMap<PsiElement, TokenNode> extractedTokenNodes;
    HashMap<String, Pair<PsiElement, Symbol>> currentExtractedSymbols = new HashMap<>();
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
        assert this.psiFile != null;
        makeNewScope(); //create top level scope
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

    private PsiElement[] getScopeElements(PsiElement node){
        PsiElement[] items = PsiTreeUtil.collectElements(node.getFirstChild(), new PsiElementFilter() {
            @Override
            public boolean isAccepted(@NotNull PsiElement psiElement) {
                PyBaseElementImpl pyBaseElement = (PyBaseElementImpl) psiElement;
                if (((PyBaseElementImpl<?>) psiElement).getElementType() != null) {
                    return true;
                }
                return false;
            }
        });
        return items;
    }

    void makeNewScope(){
        HashMap<String, Pair<PsiElement, Symbol>> newScope = new HashMap<>();
        this.scopes.add(newScope);
        this.currentExtractedSymbols = newScope;
    }

    void exitScope(){
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

    void visitPyFile(PsiElement file){
        for (PsiElement element : file.getChildren()){
            this.visit(element);
        }
    }

    void visitPyAssignmentStatement(PsiElement node) {
        if(node instanceof PyAssignmentStatementImpl){
            PyAssignmentStatementImpl assignNode = (PyAssignmentStatementImpl) node;
            Iterator<PyExpression> iterator = Arrays.asList((assignNode.getRawTargets())).iterator();
            int i = 0;
            while(iterator.hasNext()){
                PsiElement target = iterator.next();
                if (target.getFirstChild() instanceof PyReferenceExpressionImpl){
                    if (!this.currentExtractedSymbols.containsKey(target.getText())) {
                        this.currentExtractedSymbols.put(target.getText(), new Pair<>(target, new Symbol(target)));
                    }
                    //need to parse type annotation and pass it in the typeAnnotationNode argument below
                    this.visitVariableLike(target, target.getTextOffset(), true, null);
                }
                else {
                    this.visit(target);
                }
                if (i > 0) {
                    this.addEdge(target,assignNode.getAssignedValue(), EdgeType.NEXT);
                }
                this.addEdge(target, assignNode.getAssignedValue(), EdgeType.COMPUTED_FROM);
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
        if (!this.currentExtractedSymbols.containsKey(targetExpression.getReferencedName())) {
            this.currentExtractedSymbols.put(targetExpression.getReferencedName(), new Pair<>(targetExpression, new Symbol(targetExpression)));
        }
        this.visitVariableLike(targetExpression, targetExpression.getTextOffset(), true, null);
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


    void visitVariableLike(PsiElement node, int startOffset, boolean canAnnotateHere, TypeAnnotationNode typeAnnotationNode){
        HashMap<String, Object> results = getSymbolForName(node, startOffset);
        TokenNode resultNode = null;
        Symbol resultSymbol = null;
        if (results.get("node") instanceof TokenNode) {
            resultNode = (TokenNode) results.get("node");
        }
        if (results.get("symbol") instanceof Symbol) {
            resultSymbol = (Symbol) results.get("symbol");
        }
        System.out.println(currentParentNode);
        if (resultNode != null && resultSymbol != null){
            this.addEdge(resultNode, resultSymbol, EdgeType.OCCURRENCE_OF);
        }

    }

    HashMap<String, Object> getSymbolForName(PsiElement node, int startOffset) {
        HashMap<String, Object> results = new HashMap<>();
        if (node.getChildren().length == 0) {
            TokenNode newNode = new TokenNode(node.getText(), startOffset);
            this.addTerminal(newNode);
            //this.extractedTokenNodes.put(node, newNode);
            //if(!this.currentExtractedSymbols.containsKey(node)) this.currentExtractedSymbols.put(node, new Symbol(node));
            //this.addTerminal(newNode);
            //TODO special underscored items
            results.put("node", newNode);

            //symbol = getScopeSymbol(node);
            System.out.println("here");
            System.out.println(this.currentExtractedSymbols);

        }

        //If nodes are of form X.Y or X.Y.Z (including attributes with form: self.X
        else if (node.getFirstChild() instanceof PyReferenceExpressionImpl) {
            PyReferenceExpression prefix = (PyReferenceExpressionImpl) node.getFirstChild();
            if (prefix.getReferencedName() != null) {
                this.visit(prefix);
                this.addTerminal(new TokenNode(".", node.getTextOffset()));

                // can appear in targetExpression and ReferenceExpression
                if (node instanceof PyReferenceExpressionImpl) {
                    PyReferenceExpression refSuffix = (PyReferenceExpressionImpl) node;
                    this.addTerminal(new TokenNode(refSuffix.getName()));
                } else if (node instanceof PyTargetExpressionImpl) {
                    PyTargetExpression targetSuffix = (PyTargetExpressionImpl) node;
                    this.addTerminal(new TokenNode(targetSuffix.getName()));
                }
                if (prefix.getReferencedName().equals("self")) {
                    //PyAttribute does not exist in PSI
                    //Only added to match output from Typilus
                    TokenNode newNode = new TokenNode("PyAttribute", startOffset);
                    results.put("node", newNode);
                    this.addEdge(this.currentParentNode, newNode, EdgeType.CHILD);
                    this.addTerminal(new TokenNode(node.getText(), startOffset));
                }
            }

        }
        Symbol symbol = getScopeSymbol(node);
        if (symbol != null) results.put("symbol", symbol);
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
            // resolve a variable reference, or find function, or find parameters
            if (reference != null ||
                    node.getParent().getClass() == PyFunctionImpl.class ||
                    node.getClass() == PyNamedParameterImpl.class
            ) {
                for (Map.Entry<String, Pair<PsiElement, Symbol>> entry : this.currentExtractedSymbols.entrySet()){
                    if (entry.getKey().equals(node.getText())){
                        return entry.getValue().getSecond(); //returns the corresponding Symbol
                    }
                }
            }
        }
        System.out.println("printing null node");
        System.out.println(node.getText());
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
            try{
                this.visitPyStatementList(classNode.getStatementList());
            }finally {
                this.exitScope();
            }
        }

    }

}
