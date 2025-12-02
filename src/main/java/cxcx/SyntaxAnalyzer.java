package cxcx;

import java.util.*;
import cxcx.LexicalAnalyzer.*;

public class SyntaxAnalyzer {
    private List<Token> tokens;
    private int index = 0;

    public SyntaxAnalyzer(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ASTNode parse() {
        return parseModel();
    }

    // 解析模型
    private ASTNode parseModel() {
        expect("ModelProto");
        expect("{");
        ASTNode modelBody = parseModelBody();
        expect("}");
        return modelBody;
    }

    private ASTNode parseModelBody() {
        // 按照文法规则解析模型
        ASTNode modelBody = new ASTNode("ModelBody");
        modelBody.addChild(parseIrVersion());
        modelBody.addChild(parseProducerName());
        modelBody.addChild(parseProducerVersion());
        modelBody.addChild(parseDomain());
        modelBody.addChild(parseModelVersion());
        modelBody.addChild(parseDocString());
        modelBody.addChild(parseGraph());
        modelBody.addChild(parseOpsetImports());
        return modelBody;
    }

    private ASTNode parseIrVersion() {
        expect("ir_version");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("ir_version", value, token.line, token.column);
    }

    private ASTNode parseProducerName() {
        expect("producer_name");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("producer_name", value, token.line, token.column);
    }
    private ASTNode parseProducerVersion() {
        expect("producer_version");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("producer_version", value, token.line, token.column);
    }

    private ASTNode parseDomain() {
        expect("domain");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("domain", value, token.line, token.column);
    }

    private ASTNode parseModelVersion() {
        expect("model_version");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("model_version", value, token.line, token.column);
    }

    private ASTNode parseDocString() {
        expect("doc_string");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("doc_string", value, token.line, token.column);
    }

    private ASTNode parseGraph() {
        expect("graph");
        expect("{");
        ASTNode graphNode = new ASTNode("graph");
        graphNode.addChild(parseGraphBody());
        expect("}");
        return graphNode;
    }

    private ASTNode parseGraphBody() {
        ASTNode graphBody = new ASTNode("graph_body");
        graphBody.addChild(parseName());

        // 跟踪已解析的部分
        boolean hasNodeList = false;
        boolean hasInputList = false;
        boolean hasOutputList = false;
        boolean hasInitializerList = false;

        while (index < tokens.size() && !currentToken().value.equals("}")) {
            String tokenValue = currentToken().value;

            switch (tokenValue) {
                case "node":
                    if (hasInputList || hasOutputList || hasInitializerList) {
                        throw new SyntaxError("node must come before input/output/initializer",
                                currentToken().line, currentToken().column);
                    }
                    graphBody.addChild(parseNodeList());
                    hasNodeList = true;
                    break;

                case "input":
                    if (hasOutputList || hasInitializerList) {
                        throw new SyntaxError("input must come before output/initializer",
                                currentToken().line, currentToken().column);
                    }
                    graphBody.addChild(parseInputList());
                    hasInputList = true;
                    break;

                case "output":
                    if (hasInitializerList) {
                        throw new SyntaxError("output must come before initializer",
                                currentToken().line, currentToken().column);
                    }
                    graphBody.addChild(parseOutputList());
                    hasOutputList = true;
                    break;

                case "initializer":
                    graphBody.addChild(parseInitializerList());
                    hasInitializerList = true;
                    break;

                default:
                    // 尝试错误恢复：跳过意外的token
                    System.err.println("Warning: Unexpected token in graph body: " + tokenValue);
                    index++;
                    break;
            }
        }

        // 验证必须存在的部分
        if (!hasNodeList) {
            throw new SyntaxError("Missing node list", currentToken().line, currentToken().column);
        }
        if (!hasInputList) {
            throw new SyntaxError("Missing input list", currentToken().line, currentToken().column);
        }
        if (!hasOutputList) {
            throw new SyntaxError("Missing output list", currentToken().line, currentToken().column);
        }

        return graphBody;
    }

    private ASTNode parseName() {
        expect("name");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("name", value, token.line, token.column);
    }

    private ASTNode parseNodeList() {
        expect("node");
        expect("{");
        ASTNode nodeList = new ASTNode("node_list");
        nodeList.addChild(parseNodeDef());
        while (currentToken().value.equals("node")) {
            nodeList.addChild(parseNodeDef());
        }
        expect("}");
        return nodeList;
    }

    private ASTNode parseNodeDef() {
        ASTNode nodeDef = new ASTNode("node");
        nodeDef.addChild(parseOpType());
        nodeDef.addChild(parseName());
        nodeDef.addChild(parseInputDef());
        nodeDef.addChild(parseOutputDef());
        // 解析attribute_list如果有
        if (currentToken().value.equals("attribute")) {
            nodeDef.addChild(parseAttributeList());
        }
        return nodeDef;
    }
    private ASTNode parseAttributeList() {
        expect("attribute");
        expect("{");
        ASTNode attributeList = new ASTNode("attribute_list");
        attributeList.addChild(parseAttributeDef());  // 解析一个属性定义

        // 解析可能存在的重复属性定义
        while (currentToken().value.equals("attribute")) {
            attributeList.addChild(parseAttributeDef());
        }

        expect("}");
        // 解析可能存在的重复属性列表（attribute_repeats）
        if (currentToken().value.equals("attribute")) {
            attributeList.addChild(parseAttributeList());
        }

        return attributeList;
    }

    private ASTNode parseAttributeDef() {
        ASTNode attributeDef = new ASTNode("attribute");

        // 解析name_def
        expect("name");
        expect("=");
        String name = currentToken().value;
        Token token = currentToken();
        index++;
        attributeDef.addChild(new ASTNode("name", name, token.line, token.column));

        // 解析value_def
        expect("value");
        expect("=");
        String value = currentToken().value;
        Token token1 = currentToken();
        index++;
        attributeDef.addChild(new ASTNode("Value", value, token1.line, token1.column));

        return attributeDef;
    }

    private ASTNode parseOpType() {
        expect("op_type");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("op_type", value, token.line, token.column);
    }

    private ASTNode parseInputDef() {
        expect("input");
        if (currentToken().value.equals("{")) { // 块格式
            return parseInputList();
        } else if (currentToken().value.equals("=")) { // 数组格式
            expect("=");
            expect("[");
            return parseInputArr();
        }
        throw new SyntaxError("Expected { or [", currentToken().line, currentToken().column);
    }

    private ASTNode parseInputArr() {
        ASTNode arrNode = new ASTNode("input_arr");
        arrNode.addChild(new ASTNode("Value", currentToken().value, currentToken().line, currentToken().column));
        index++;

        while (currentToken().value.equals(",")) {
            index++; // 跳过逗号
            arrNode.addChild(new ASTNode("Value", currentToken().value, currentToken().line, currentToken().column));
            index++;
        }
        expect("]");
        return arrNode;
    }

    private ASTNode parseInputList() {
        expect("input");
        expect("{");
        ASTNode inputList = new ASTNode("input_list");
        // 使用do-while确保至少解析一个valueInfo
        do {
            inputList.addChild(parseValueInfoDef());
        } while (currentToken().value.equals("input")); // 检查是否有后续input条目
        expect("}");
        return inputList;
    }

    private ASTNode parseOutputDef() {
        expect("output");
        if (currentToken().value.equals("{")) { // 块格式
            return parseOutputList();
        } else if (currentToken().value.equals("=")) { // 数组格式
            expect("=");
            expect("[");
            return parseOutputArr();
        }
        throw new SyntaxError("Expected { or [", currentToken().line, currentToken().column);
    }

    // 解析数组格式
    private ASTNode parseOutputArr() {
        ASTNode arrNode = new ASTNode("output_arr");
        arrNode.addChild(new ASTNode("Value", currentToken().value, currentToken().line, currentToken().column));
        index++;

        while (currentToken().value.equals(",")) {
            index++; // 跳过逗号
            arrNode.addChild(new ASTNode("Value", currentToken().value, currentToken().line, currentToken().column));
            index++;
        }
        expect("]");
        return arrNode;
    }
    private ASTNode parseOutputList() {
        expect("output");
        if (currentToken().value.equals("{")) {
            expect("{");
            ASTNode outputList = new ASTNode("output_list");
            do {
                outputList.addChild(parseValueInfoDef());
            } while (currentToken().value.equals("output"));
            expect("}");
            return outputList;
        }
        throw new SyntaxError("Expected { for output list", currentToken().line, currentToken().column);
    }

    private ASTNode parseInitializerList() {
        if (index >= tokens.size() || !currentToken().value.equals("initializer")) {
            return new ASTNode("initializer_list"); // 返回空节点
        }

        ASTNode initializerList = new ASTNode("initializer_list");
        while (currentToken().value.equals("initializer")) {
            expect("initializer");
            expect("{");
            initializerList.addChild(parseTensorDef());
            while (currentToken().value.equals("initializer")) {
                initializerList.addChild(parseTensorDef());
            }
            expect("}");
        }
        return initializerList.getChildren().isEmpty() ? null : initializerList;
    }

    private ASTNode parseValueInfoDef() {
        ASTNode valueInfoDef = new ASTNode("value_info");
        // 确保当前是name字段
        if (!currentToken().value.equals("name")) {
            throw new SyntaxError("Expected name in ValueInfoDef but found " + currentToken().value,
                    currentToken().line,
                    currentToken().column
            );
        }
        valueInfoDef.addChild(parseName());
// 确保接下来是type字段
        if (!currentToken().value.equals("type")) {
            throw new SyntaxError("Expected type in ValueInfoDef but found " + currentToken().value,
                    currentToken().line,
                    currentToken().column
            );
        }
        valueInfoDef.addChild(parseTypeDef());
        return valueInfoDef;
    }

    private ASTNode parseTypeDef() {
        expect("type");
        expect("{");
        ASTNode tensorTypeDef = parseTensorTypeDef();
        expect("}");
        return tensorTypeDef;
    }

    private ASTNode parseTensorTypeDef() {
        expect("tensor_type");
        expect("{");
        ASTNode tensorTypeDef = new ASTNode("tensor_type");
        while (!currentToken().value.equals("}")) {
            switch (currentToken().value) {
                case "elem_type":
                    tensorTypeDef.addChild(parseElemType());
                    break;
                case "shape":
                    tensorTypeDef.addChild(parseShapeDef());
                    break;
                default:
                    index++; // 跳过未知字段
            }
        }


        expect("}");
        return tensorTypeDef;
    }

    private ASTNode parseElemType() {
        expect("elem_type");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        if (value.equals("int") || value.equals("float") || value.equals("string") || value.equals("bool")) {
            return new ASTNode("elem_type", value, token.line, token.column);
        }
        throw new SyntaxError("Expected valid elem_type but found " + value,
                token.line,
                token.column
        );
    }

    private ASTNode parseShapeDef() {
        expect("shape");
        expect("{");
        ASTNode shapeDef = new ASTNode("shape");
        shapeDef.addChild(parseDimList());
        expect("}");
        return shapeDef;
    }

    private ASTNode parseDimList() {
        ASTNode dimList = new ASTNode("dim_list");
        while (currentToken().value.equals("dim")) {
            dimList.addChild(parseDimDef());
        }
        return dimList;
    }

    private ASTNode parseDimDef() {
        expect("dim");
        expect("{");
        ASTNode dimNode = new ASTNode("dim");
        String tokenValue = currentToken().value;
        Token token = currentToken();
        if (!tokenValue.equals("dim_value") && !tokenValue.equals("dim_param")) {
            throw new SyntaxError("Expected dim_value or dim_param but found " + tokenValue,
                    token.line,
                    token.column
            );
        }
        expect(tokenValue);
        expect("=");
        String value = currentToken().value;
        Token token1 = currentToken();
        index++;
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            dimNode.addChild(new ASTNode("dim_value",value, token1.line, token1.column)); // 如果是数字，dim_value
        } else {
            dimNode.addChild(new ASTNode("dim_param",value, token1.line, token1.column));
            // return new ASTNode("dim_param", value);  // 如果是字符串，dim_param
        }
        expect("}");
        return dimNode;
    }

    private ASTNode parseTensorDef() {
        ASTNode tensorDef = new ASTNode("tensor");
        tensorDef.addChild(parseName());
        tensorDef.addChild(parseDataTypeDef());
        tensorDef.addChild(parseDimsDef());
        tensorDef.addChild(parseRawDataDef());
        return tensorDef;
    }

    private ASTNode parseDataTypeDef() {
        expect("data_type");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        if (value.equals("int") || value.equals("float") || value.equals("string") || value.equals("bool")) {
            return new ASTNode("data_type", value, token.line, token.column);
        }
        throw new SyntaxError("Expected valid data_type but found " + value,
                token.line,
                token.column
        );
    }

    private ASTNode parseDimsDef() {
        expect("dims");
        expect("=");
        ASTNode dimsDef = new ASTNode("dims");
        // 直接消耗后续连续 INTEGER
        while (currentToken().type == TokenType.INTEGER) {
            dimsDef.addChild(new ASTNode("dim_value", currentToken().value, currentToken().line, currentToken().column));
            index++;
        }

        return dimsDef;
    }

    private ASTNode parseRawDataDef() {
        if (currentToken().value.equals("raw_data")) {
            expect("raw_data");
            expect("=");
            String value = currentToken().value;
            Token token = currentToken();
            index++;
            return new ASTNode("raw_data", value, token.line, token.column);
        }
        return null;
    }

    private ASTNode parseOpsetImports() {
        // 记录opset_imports的起始位置
        LexicalAnalyzer.Token startToken = currentToken();
        ASTNode importsNode = new ASTNode("opset_imports", startToken.line, startToken.column);

        while (currentToken().value.equals("opset_import")) {
            importsNode.addChild(parseSingleOpsetImport());
        }
        return importsNode;
    }

    private ASTNode parseSingleOpsetImport() {
        // 获取当前opset_import的位置
        Token opsetToken = expect("opset_import");
        expect("{");

        ASTNode importNode = new ASTNode("opset_import", opsetToken.line, opsetToken.column);
        importNode.addChild(parseDomain());
        importNode.addChild(parseVersionDef());

        expect("}");
        return importNode;
    }

    private ASTNode parseVersionDef() {
        expect("version");
        expect("=");
        String value = currentToken().value;
        Token token = currentToken();
        index++;
        return new ASTNode("version", value, token.line, token.column);
    }


    // 获取当前的Token
    private Token currentToken() {
        return tokens.get(index);
    }

    // 期待某个Token，并且移动到下一个Token
    private Token expect(String expectedValue) {
        if (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.value.equals(expectedValue)) {
                index++;
                return token;
            } else {
                throw new SyntaxError("Expected " + expectedValue + " but found " + token.value + " ",
                        token.line,
                        token.column
                );
            }
        } else {
            throw new SyntaxError("Unexpected end of input. Expected " + expectedValue + " ",
                    -1,
                    -1
            );
        }
    }

}

class SyntaxError extends RuntimeException {
    private final int line;
    private final int column;
    public SyntaxError(String message, int line, int column) {
        super(message + " [at line" + line + ":" + column + "]");
        this.line = line;
        this.column = column;
    }
}

// AST节点类
class ASTNode {
    String name;
    String value;
    private int line;
    private int column;
    List<ASTNode> children = new ArrayList<>();

    ASTNode(String name) {
        this.name = name;
    }

    public ASTNode(String name, int line, int column) {
        this.name = name;
        this.line = line;
        this.column = column;
    }

    public ASTNode(String name, String value, int line, int column) {
        this(name, line, column);
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
    public int getLine() {
        return line;
    }
    public int getColumn() {
        return column;
    }

    public List<ASTNode> getChildren() {
        return children;
    }

    void addChild(ASTNode child) {
        children.add(child);
    }

    private List<SyntaxError> errors = new ArrayList<>();

    public void addError(SyntaxError error) {
        errors.add(error);
    }

    public List<SyntaxError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    void print() {
        print("", true);
    }

    private void print(String indent, boolean last) {
        // 显示改进：处理嵌套结构
        String displayName = name;
        if (name.equals("GraphBody")) {
            displayName = "";
        }

        System.out.println(indent + (last ? "+-- " : "|-- ") + displayName + (value != null ? ": " + value : ""));
        for (int i = 0; i < children.size(); i++) {
            // 隐藏GraphBody节点层级
            boolean isLast = i == children.size() - 1;
            if (children.get(i).name.equals("GraphBody")) {
                children.get(i).print(indent, isLast);
            } else {
                children.get(i).print(indent + (last ? "    " : "|   "), isLast);
            }
        }
    }
}
