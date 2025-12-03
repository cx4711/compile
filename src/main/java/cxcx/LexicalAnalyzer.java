package cxcx;

import java.io.IOException;
import java.util.regex.*;
import java.util.*;

/**
 * 词法分析器（Lexical Analyzer）
 * 用于将输入的源代码字符串分解为一系列有意义的词法单元（Token）
 */
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

    /**
     * Token类型枚举
     * 定义各种词法单元的类型
     */
    enum TokenType {
        KEYWORD, BYTES, IDENTIFIER, INTEGER, STRING, SYMBOL, ERROR
    }

    /**
     * Token类
     * 表示一个词法单元，包含类型、值及其在源代码中的位置信息
     */
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

    /**
     * 主分析方法：将源代码字符串分解为Token序列
     *
     * @param sourceCode 待分析的源代码字符串
     * @return Token列表，按源代码中的出现顺序排列
     * @throws IllegalArgumentException 当遇到无法识别的字符时抛出异常
     */
    public List<Token> analyze(String sourceCode) {
        List<Token> tokens = new ArrayList<>();

        // 编译正则表达式，匹配所有可能的词法单元模式
        // 注意：模式的顺序很重要，需要优先匹配更具体的模式
        Pattern pattern = Pattern.compile(WHITESPACE + "|" + KEYWORDS + "|" + BYTES + "|" + INTEGER + "|" + STRING + "|" + IDENTIFIER + "|" + SYMBOLS);
        Matcher matcher = pattern.matcher(sourceCode);

        // 遍历所有匹配项
        while (matcher.find()) {
            // 获取匹配的文本
            String match = matcher.group();
            // 跳过空白字符
            if (match.matches(WHITESPACE)) {
                updatePosition(match); // 更新位置但跳过token生成
                continue;
            }

            TokenType type = getTokenType(match);// 确定Token类型
            int tokenline = lineNumber;// 记录Token出现的位置
            int tokenColumn = column;
            updatePosition(match);// 更新当前位置信息（为下一个Token做准备）

            if (type == TokenType.ERROR) {
                // 如果匹配不到任何合法的 token, 抛出异常
                throw new IllegalArgumentException("Unexpected character: " + match);
            }
            // 将Token添加到结果列表
            tokens.add(new Token(type, match, tokenline, tokenColumn));
        }
        return tokens;
    }

    /**
     * 更新源代码位置信息（行号和列号）
     *
     * @param text 当前处理的文本
     */
    private void updatePosition(String text) {
        int newlines = countNewlines(text);// 计算文本中的换行符数量
        if (newlines > 0) {
            // 如果有换行符，增加行号并重置列号
            lineNumber += newlines;
            int lastNewline = text.lastIndexOf('\n');
            column = text.length() - lastNewline; // 修正列号计算
        } else {
            // 没有换行符，只增加列号
            column += text.length();
        }
    }

    /**
     * 计算字符串中换行符('\n')的数量
     *
     * @param text 待检查的字符串
     * @return 换行符的数量
     */
    private int countNewlines(String text) {
        return (int) text.chars().filter(c -> c == '\n').count();
    }

    /**
     * 根据匹配的文本确定Token类型
     * 注意：匹配顺序很重要，应先匹配更具体的模式（如关键字）
     *
     * @param match 匹配到的文本
     * @return 对应的TokenType枚举值
     */
    private TokenType getTokenType(String match) {
        // 按优先级检查各种模式
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
        // 如果都不匹配，返回错误类型
        return TokenType.ERROR;
    }
}
