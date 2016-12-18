package slpl.syntax;

import slpl.err.ParseException;
import slpl.util.Operator;
import slpl.syntax.lexical.Token;
import slpl.syntax.lexical.TokenType;
import slpl.syntax.lexical.TokenTypeClass;
import slpl.ast.*;
import slpl.ast.Boolean;
import slpl.ast.Number;
import slpl.util.StringConcatenator;
import slpl.util.TokenStream;

import java.util.*;

public class ExpressionParser {

    private static HashMap<Token, FunctionCallWrapper> functionCalls = new HashMap<>();

    public static AST parseExpression(TokenStream ts) throws ParseException {
        int start = ts.position();
        recognizeExpression(ts);
        int end = ts.position();
        ts.setPosition(start);
        Stack<AST> s = new Stack<>();
        for (Token t : transformInfixToPostfix(ts, end)) {
            TokenType tokenType = t.type();
            if (tokenType.instanceOf(TokenTypeClass.OPERATOR)) {
                int arity = Operator.fromToken(t).arity();
                AST[] arguments = new AST[arity];
                for (int i = arity - 1; i >= 0; --i) {
                    arguments[i] = s.pop();
                }
                s.push(toOperationNode(t, arguments));
            } else if (tokenType.instanceOf(TokenTypeClass.VALUE)) {
                s.push(toValueNode(t));
            } else if (tokenType == TokenType.FID) {
                s.push(functionCalls.get(t).node);
            } else {
                throw ParseException.unexpected(t);
            }
        }
        assert ts.position() == end;
        return s.pop();
    }

    public static AST toOperationNode(Token t, AST[] args) {
        TokenType tokenType = t.type();
        if (tokenType.instanceOf(TokenTypeClass.OPERATOR)) {
            if (tokenType.instanceOf(TokenTypeClass.UNARY_OPERATOR)) {
                if (tokenType.instanceOf(TokenTypeClass.ARITHMETIC_OPERATOR)) {
                    return new UnaryArithmeticOperation(Operator.fromToken(t), args[0]);
                } else if (tokenType.instanceOf(TokenTypeClass.LOGICAL_OPERATOR)) {
                    return new UnaryLogicalOperation(Operator.fromToken(t), args[0]);
                }
            } else if (tokenType.instanceOf(TokenTypeClass.BINARY_OPERATOR)) {
                if (tokenType.instanceOf(TokenTypeClass.ARITHMETIC_OPERATOR)) {
                    return new BinaryArithmeticOperation(Operator.fromToken(t), args[0], args[1]);
                } else if (tokenType.instanceOf(TokenTypeClass.LOGICAL_OPERATOR)) {
                    return new BinaryLogicalOperation(Operator.fromToken(t), args[0], args[1]);
                } else if (tokenType.instanceOf(TokenTypeClass.RELATIONAL_OPERATOR)) {
                    return new RelationalOperation(Operator.fromToken(t), args[0], args[1]);
                }
            }
        }
        throw new IllegalArgumentException(String.format("%s is not defined for argument(s) (%s)", t, StringConcatenator.concatenate(", ", args)));
    }

    public static AST toValueNode(Token t) {
        switch (t.type()) {
            case TRUE:
                return new Boolean(true);
            case FALSE:
                return new Boolean(false);
            case ID:
                return new Identifier(t.content());
            case NUM:
                return new Number(t.content());
            case STRING:
                return new Str(t.content());
            case NULL:
                return new Null();
        }
        throw new IllegalArgumentException(t + " is not a value");
    }

    public static void recognizeExpression(TokenStream ts) throws ParseException {
        recognizeTerm(ts);
        if (ts.hasNext(TokenType.ADD, TokenType.SUB, TokenType.OR) || (ts.hasNext() && ts.inspect().type().instanceOf(TokenTypeClass.RELATIONAL_OPERATOR))) {
            ts.consume();
            recognizeExpression(ts);
        }
    }

    private static void recognizeTerm(TokenStream ts) throws ParseException {
        recognizeFactor(ts);
        if (ts.hasNext(TokenType.MUL, TokenType.DIV, TokenType.AND)) {
            ts.consume();
            recognizeTerm(ts);
        }
    }

    private static void recognizeFactor(TokenStream ts) throws ParseException {
        if (ts.hasNext(TokenType.NOT, TokenType.SUB)) {
            if (ts.hasNext(TokenType.SUB)) {
                Token t = ts.inspect();
                ts.replace(new Token(TokenType.ADDINV, t.content(), t.row(), t.col()));
            }
            ts.consume();
            recognizeFactor(ts);
        } else if (ts.hasNext(TokenType.LPAR)) {
            ts.consume();
            recognizeExpression(ts);
            ts.expect(TokenType.RPAR);
            ts.consume();
        } else {
            ts.expect(TokenType.NUM, TokenType.ID, TokenType.TRUE, TokenType.FALSE, TokenType.STRING, TokenType.NULL);
            int lookaheadStart = ts.position();
            Token t = ts.consume();
            if (ts.hasNext(TokenType.LPAR)) {
                ts.setPosition(lookaheadStart);
                t = new Token(TokenType.FID, t.content(), t.row(), t.col());
                ts.replace(t);
                FunctionCall functionCall = FunctionCallParser.parseFunctionCall(ts);
                functionCalls.put(t, new FunctionCallWrapper(ts.position(), functionCall));
            }

        }
    }

    public static List<Token> transformInfixToPostfix(TokenStream ts, int end) throws ParseException {
        LinkedList<Token> outputQueue = new LinkedList<>();
        Stack<Token> operatorStack = new Stack<>();
        while (ts.position() != end) {
            Token t = ts.consume();
            TokenType tokenType = t.type();
            if (tokenType.instanceOf(TokenTypeClass.VALUE)) {
                outputQueue.add(t);
            } else if (tokenType.instanceOf(TokenTypeClass.OPERATOR)) {
                enqueuePrecedentOperators(outputQueue, operatorStack, Operator.fromToken(t));
                operatorStack.push(t);
            } else if (tokenType == TokenType.FID) {
                outputQueue.add(t);
                ts.setPosition(functionCalls.get(t).end);
            } else if (tokenType == TokenType.LPAR) {
                operatorStack.push(t);
            } else if (tokenType == TokenType.COMMA) {
                try {
                    while (operatorStack.peek().type() != TokenType.LPAR) {
                        outputQueue.add(operatorStack.pop());
                    }
                } catch (EmptyStackException e) {
                    throw ParseException.bracketMismatch(t); // TODO: throw more informative error
                }
            } else if (tokenType == TokenType.RPAR) {
                try {
                    while (operatorStack.peek().type() != TokenType.LPAR) {
                        outputQueue.add(operatorStack.pop());
                    }
                    operatorStack.pop();
                    if (operatorStack.peek().type() == TokenType.FID) {
                        outputQueue.add(operatorStack.pop());
                    }
                } catch (EmptyStackException e) {
                    throw ParseException.bracketMismatch(t);
                }
            }
        }
        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek().type().instanceOf(TokenTypeClass.BRACKET)) {
                throw ParseException.bracketMismatch(operatorStack.peek());
            }
            outputQueue.add(operatorStack.pop());
        }
        return outputQueue;
    }

    /**
     * Add to <b>outputQueue</b> every operator on <b>operatorStack</b> that has a higher precedence than <b>o1</b>.
     *
     * @param outputQueue
     * @param operatorStack
     * @param o1
     */
    private static void enqueuePrecedentOperators(LinkedList<Token> outputQueue, Stack<Token> operatorStack, Operator o1) {
        while (!operatorStack.isEmpty() && operatorStack.peek().type().instanceOf(TokenTypeClass.OPERATOR)) {
            Operator o2 = Operator.fromToken(operatorStack.peek());
            if (o1.fixity() == Operator.Fixity.LEFT && o1.precedence() <= o2.precedence()) {
                outputQueue.add(operatorStack.pop());
            } else if (o1.fixity() == Operator.Fixity.RIGHT && o1.precedence() < o2.precedence()) {
                outputQueue.add(operatorStack.pop());
            } else {
                break;
            }
        }
    }

    private static class FunctionCallWrapper {

        private int end;
        private FunctionCall node;

        public FunctionCallWrapper(int end, FunctionCall node) {
            this.end = end;
            this.node = node;
        }

    }

}
