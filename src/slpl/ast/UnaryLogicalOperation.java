package slpl.ast;

import slpl.PrimitiveType;
import slpl.err.TypeCheckException;
import slpl.util.Context;
import slpl.util.Operator;
import slpl.util.TypeCheckerContext;

public class UnaryLogicalOperation extends AST {

    private Operator operator;
    private AST arg;

    public UnaryLogicalOperation(Operator operator, AST arg) {
        this.operator = operator;
        this.arg = arg;
    }

    @Override
    public AST evaluate(Context context) {
        Boolean b = (Boolean) arg.evaluate(context);
        switch (operator) {
            case NOT:
                return new Boolean(!b.value());
        }
        throw new UnsupportedOperationException(operator.toString());
    }

    @Override
    public String typeCheck(TypeCheckerContext typeCheckerContext) throws TypeCheckException {
        String argType = arg.typeCheck(typeCheckerContext);
        String booleanType = PrimitiveType.BOOLEAN.typeName();
        if(argType.equals(booleanType)) {
            return booleanType;
        }
        throw TypeCheckException.undefinedOperation(operator, argType);
    }

    @Override
    public String toString() {
        return String.format("(%s %s)", operator, arg);
    }
}
