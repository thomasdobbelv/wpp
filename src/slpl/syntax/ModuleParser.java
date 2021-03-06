package slpl.syntax;

import slpl.err.ParseException;
import slpl.syntax.lexical.TokenType;
import slpl.ast.Module;
import slpl.util.TokenStream;

public class ModuleParser {

    public static Module parseModule(TokenStream ts) throws ParseException {
        ts.expect(TokenType.MODULE);
        ts.consume();
        ts.expect(TokenType.ID);
        String name = ts.consume().content();
        return new Module(name, BlockParser.parseBlock(ts));
    }

}
