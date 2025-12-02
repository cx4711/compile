package cxcx;

import java.io.IOException;
import java.util.regex.*;
import java.util.*;

public class LexicalAnalyzer {
    // 定义正则表达式
    private static final String KEYWORDS = "ModelProto|graph|name|node|input|output|op_type|attribute|initializer|doc_string|domain|model_version|producer_name|producer_version|ir_version|tensor_type|elem_type|shape|dim_value|dim_param|dims|dim|raw_data|opset_import|data_type|version|value|type|int|float|string|bool";
    private static final String INTEGER = "[-+]?\\d+[lL]?"; // 支持后缀
    private static final String ESCAPE = "\\\\([btnfr\"'\\\\])";
    private static final String STRING = "\"(?:\\\\\"|[^\"])*\"";
    private static final String BYTES = "[0-9A-Fa-f]+b";
    private static final String IDENTIFIER = "[a-zA-Z_][a-zA-Z0-9_]*";
    private static final String SYMBOLS = "[\\[\\]{}=,]";  // 包括方括号、花括号、逗号等
    private static final String WHITESPACE = "\\s+";

    private int lineNumber = 1;
    private int column = 1;

    // Token类型
    enum TokenType {

        KEYWORD, BYTES, IDENTIFIER, INTEGER, STRING, SYMBOL, ERROR
    }

    // Token类
    static class Token {
        TokenType type;
        String value;
        int line;  // 行号
        int column;  // 列号

        public Token(TokenType type, String value, int line, int column) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.column = column;
        }
    }

    public List<Token> analyze(String sourceCode) {
        List<Token> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile(WHITESPACE + "|" + KEYWORDS + "|" + BYTES + "|" + INTEGER + "|" + STRING + "|" + IDENTIFIER + "|" + SYMBOLS);
        Matcher matcher = pattern.matcher(sourceCode);

        while (matcher.find()) {
            String match = matcher.group();
            // 跳过空白字符
            if (match.matches(WHITESPACE)) {
                updatePosition(match); // 更新位置但跳过token生成
                continue;
            }

            TokenType type = getTokenType(match);
            int tokenline = lineNumber;
            int tokenColumn = column;
            updatePosition(match);

            if (type == TokenType.ERROR) {
                // 如果匹配不到任何合法的 token, 抛出异常
                throw new IllegalArgumentException("Unexpected character: " + match);
            }
            tokens.add(new Token(type, match, tokenline, tokenColumn));
        }
        return tokens;
    }

    private void updatePosition(String text) {
        int newlines = countNewlines(text);
        if (newlines > 0) {
            lineNumber += newlines;
            int lastNewline = text.lastIndexOf('\n');
            column = text.length() - lastNewline; // 修正列号计算
        } else {
            column += text.length();
        }
    }

    private int countNewlines(String text) {
        return (int) text.chars().filter(c -> c == '\n').count();
    }

    // 获取Token类型
    private TokenType getTokenType(String match) {
        if (match.matches(KEYWORDS)) {
            return TokenType.KEYWORD;
        } else if (match.matches(INTEGER)) {
            return TokenType.INTEGER;
        } else if (match.matches(STRING)) {
            return TokenType.STRING;
        } else if (match.matches(BYTES)) {
            return TokenType.BYTES;
        } else if (match.matches(IDENTIFIER)) {
            return TokenType.IDENTIFIER;
        } else if (match.matches(SYMBOLS)) {
            return TokenType.SYMBOL;
        }
        return TokenType.ERROR;
    }
}
