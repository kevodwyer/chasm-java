package wasm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Parser {
    public static class ProgramNode {
        String type;
        public ProgramNode(String type) {
            this.type = type;
        }
    }
    public static class ExpressionNode extends ProgramNode{
        String value;
        public ExpressionNode(String type, String value) {
            super(type);
            this.value = value;
        }
    }
    public static class StatementNode extends ProgramNode{
        ExpressionNode value;
        public StatementNode(String type, ExpressionNode value) {
            super(type);
            this.value = value;
        }
    }
    public static class NumberLiteralNode extends ExpressionNode {
        NumberLiteralNode(String value) {
            super("numberLiteral", value);
        }
    }
    public static class BinaryExpressionNode extends ExpressionNode {
        public final ExpressionNode left;
        public final ExpressionNode right;
        BinaryExpressionNode(ExpressionNode left, ExpressionNode right, String operator) {
            super("binaryExpression", operator);
            this.left = left;
            this.right = right;
        }
    }
    public static class IdentifierNode extends ExpressionNode {
        IdentifierNode(String value) {
            super("identifier", value);
        }
    }
    public static class PrintStatementNode extends StatementNode {
        PrintStatementNode(ExpressionNode value) {
            super("printStatement", value);
        }
    }
    public static class SetPixelStatementNode extends StatementNode {
        ExpressionNode x;
        ExpressionNode y;
        SetPixelStatementNode(ExpressionNode x, ExpressionNode y, ExpressionNode color) {
            super("setpixelStatement", color);
            this.x = x;
            this.y = y;
        }
    }
    public static class WhileStatementNode extends StatementNode {
        List<StatementNode> statements;
        WhileStatementNode(ExpressionNode value, List<StatementNode> statements) {
            super("whileStatement", value);
            this.statements = statements;
        }
    }
    public static class IfStatementNode extends StatementNode {
        List<StatementNode> consequent;
        List<StatementNode> alternate;
        IfStatementNode(ExpressionNode expression, List<StatementNode> consequent, List<StatementNode> alternate) {
            super("ifStatement", expression);
            this.consequent = consequent;
            this.alternate = alternate;
        }
    }
    public static class VariableDeclarationNode extends StatementNode {
        public final String name;
        VariableDeclarationNode(String name, ExpressionNode initializer) {
            super("variableDeclaration", initializer);
            this.name = name;
        }
    }
    public static class VariableAssignmentNode extends StatementNode {
        public final String name;
        VariableAssignmentNode(String name, ExpressionNode initializer) {
            super("variableAssignment", initializer);
            this.name = name;
        }
    }
    private static ExpressionNode parseExpression(Iterator<Tokenizer.Token> iterator) {
        Tokenizer.Token token = iterator.next();
        ExpressionNode node = null;
        switch(token.type) {
            case number:
                node = new NumberLiteralNode(token.value);
                break;
            case identifier:
                node = new IdentifierNode(token.value);
                break;
            case parens:
                ExpressionNode left = parseExpression(iterator);
                String operator = iterator.next().value;
                ExpressionNode right = parseExpression(iterator);
                node = new BinaryExpressionNode(left, right, operator);
                break;
            default:
                throw new IllegalStateException("Unexpected!");
        }
        return node;
    }
    private static Parser.StatementNode parsePrintStatement(Iterator<Tokenizer.Token> iterator) {
        return new PrintStatementNode(parseExpression(iterator));
    }
    private static Parser.StatementNode parseSetpixelStatement(Iterator<Tokenizer.Token> iterator) {
        return new SetPixelStatementNode(parseExpression(iterator), parseExpression(iterator), parseExpression(iterator));
    }
    private static Parser.StatementNode parseVariableDeclarationStatement(Iterator<Tokenizer.Token> iterator) {
        String name = iterator.next().value;
        String equals = iterator.next().value;
        return new VariableDeclarationNode(name, parseExpression(iterator));
    }
    private static Parser.StatementNode parseIfStatement(Iterator<Tokenizer.Token> iterator) {
        ExpressionNode expression = parseExpression(iterator);
        boolean elseStatements = false;
        List<StatementNode> consequent = new ArrayList<>();
        List<StatementNode> alternate = new ArrayList<>();
        Tokenizer.Token token = iterator.next();
        while (!(token.value.equals("endif") && token.type.equals(Tokenizer.TokenType.keyword))) {
            if (token.value.equals("else") && token.type.equals(Tokenizer.TokenType.keyword)) {
                elseStatements = true;
                token = iterator.next();
            }
            if (elseStatements) {
                alternate.add(parseStatement(token, iterator));
            } else {
                consequent.add(parseStatement(token, iterator));
            }
            token = iterator.next();
        }
        return new IfStatementNode(expression, consequent, alternate);
    }
    private static  Parser.StatementNode parseWhileStatement(Iterator<Tokenizer.Token> iterator) {
        ExpressionNode expression = parseExpression(iterator);
        List<StatementNode> statements = new ArrayList<>();
        Tokenizer.Token token = iterator.next();
        while (!(token.value.equals("endwhile")
                && token.type.equals(Tokenizer.TokenType.keyword))) {
            statements.add(parseStatement(token, iterator));
            token = iterator.next();
        }
        return new WhileStatementNode(expression, statements);
    }
    private static Parser.StatementNode parseVariableAssignment(String name, Iterator<Tokenizer.Token> iterator) {
        String equals = iterator.next().value;
        return new VariableAssignmentNode(name, parseExpression(iterator));
    }
    private static Parser.StatementNode parseStatement(Iterator<Tokenizer.Token> iterator) {
        Tokenizer.Token token = iterator.next();
        return parseStatement(token, iterator);
    }
    private static Parser.StatementNode parseStatement(Tokenizer.Token token, Iterator<Tokenizer.Token> iterator) {
        Parser.StatementNode node = null;
        if(token.type.equals(Tokenizer.TokenType.keyword)) {
            switch(token.value) {
                case "print" :
                    node = parsePrintStatement(iterator);
                    break;
                case "var" :
                    node = parseVariableDeclarationStatement(iterator);
                    break;
                case "while":
                    node = parseWhileStatement(iterator);
                    break;
                case "if":
                    node = parseIfStatement(iterator);
                    break;
                case "setpixel":
                    node = parseSetpixelStatement(iterator);
                    break;
            }
        } else if (token.type.equals(Tokenizer.TokenType.identifier)) {
            node = parseVariableAssignment(token.value, iterator);
        }
        if(node==null){
            throw new IllegalStateException("Unexpected!");
        }
        return node;
    }

    public static List<Parser.StatementNode> parse(List<Tokenizer.Token> tokens) {
        List<Parser.StatementNode> nodes = new ArrayList<>();
        Iterator<Tokenizer.Token> iterator = tokens.iterator();
        while(iterator.hasNext()) {
            nodes.add(parseStatement(iterator));
        }
        return nodes;
    }
}
