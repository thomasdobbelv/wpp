package slpl.util;

import slpl.Operator;
import slpl.Token;
import slpl.TokenType;
import slpl.TokenTypeClass;
import slpl.parse.ParseException;

import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class InfixToPostfixTransformer {

    /**
     * Dijkstra's Shunting Yard algorithm.
     *
     * @param tokens
     * @return
     */
    public static List<Token> transform(List<Token> tokens) throws ParseException {
        LinkedList<Token> outputQueue = new LinkedList<>();
        Stack<Token> operatorStack = new Stack<>();
        for (Token t : tokens) {
            if (t.isValue()) {
                outputQueue.add(t);
            } else if (t.isOperator()) {
                enqueuePrecedentOperators(outputQueue, operatorStack, Operator.fromToken(t));
                operatorStack.push(t);
            } else if (t.getType() == TokenType.LPAR) {
                operatorStack.push(t);
            } else if (t.getType() == TokenType.RPAR) {
                try {
                    while (!operatorStack.peek().getContent().equals("(")) {
                        outputQueue.add(operatorStack.pop());
                    }
                    operatorStack.pop();
                } catch (EmptyStackException e) {
                    throw ParseException.bracketMismatch(t);
                }
            }
        }
        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek().getType().getTypeClass() == TokenTypeClass.BRACKET) {
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
        while (!operatorStack.isEmpty() && operatorStack.peek().isOperator()) {
            Operator o2 = Operator.fromToken(operatorStack.peek());
            if (o1.getFixity() == Operator.Fixity.LEFT && o1.getPrecedence() <= o2.getPrecedence()) {
                outputQueue.add(operatorStack.pop());
            } else if (o1.getFixity() == Operator.Fixity.RIGHT && o1.getPrecedence() < o2.getPrecedence()) {
                outputQueue.add(operatorStack.pop());
            } else {
                break;
            }
        }
    }
}
