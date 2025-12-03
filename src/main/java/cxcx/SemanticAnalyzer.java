package cxcx;

import java.util.*;

/**
 * 语义分析器（Semantic Analyzer）
 * 负责检查抽象语法树（AST）的语义正确性，包括：
 * 1. 变量/张量定义检查
 * 2. 类型一致性检查
 * 3. 作用域和引用检查
 * 4. 操作语义验证
 */
public class SemanticAnalyzer {
    // 错误收集列表：存储所有检测到的语义错误
    private List<SemanticError> errors = new ArrayList<>();

    // 语义数据结构：存储编译过程中的符号表信息
    private final Map<String, TensorInfo> tensorRegistry = new HashMap<>(); // 张量名->类型+位置
    private final Map<String, NodeInfo> nodeRegistry = new HashMap<>();     // 节点名->操作类型+位置
    private final Set<String> outputTensors = new HashSet<>();  // 输出张量集合，用于检查输出唯一性

    // 类型兼容性规则表：定义各操作符允许的输入类型和输出类型
    private static final Map<String, TypeRule> OP_TYPE_RULES = new HashMap<>();
    static {
        // Add操作：接受FLOAT和INT类型输入，输出类型与第一个输入相同
        OP_TYPE_RULES.put("Add", new TypeRule(List.of("FLOAT", "INT"), "INPUT0"));
        // Conv操作：只接受FLOAT类型输入，输出FLOAT类型
        OP_TYPE_RULES.put("Conv", new TypeRule(List.of("FLOAT"), "FLOAT"));
        // Reshape操作：接受FLOAT和INT类型输入，输出类型与第一个输入相同
        OP_TYPE_RULES.put("Reshape", new TypeRule(List.of("FLOAT", "INT"), "INPUT0"));
    }

    /**
     * 主分析方法：对AST进行语义分析
     * 采用两遍扫描策略：
     * 第一遍：收集所有定义（符号表构建）
     * 第二遍：验证所有引用和类型
     *
     * @param modelAST 抽象语法树的根节点
     * @throws SemanticErrorException 如果发现语义错误，抛出异常包含所有错误信息
     */
    public void analyze(ASTNode modelAST) {
        errors.clear();
        collectDefinitions(modelAST);  // 第一遍：收集所有定义
        validateReferences(modelAST);  // 第二遍：验证引用和类型
        checkModelConsistency();       // 模型级检查

        if (!errors.isEmpty()) {
            throw new SemanticErrorException(errors);
        }
    }

    /**
     * 收集阶段：递归遍历AST，注册所有张量和节点定义
     *
     * @param node 当前处理的AST节点
     */
    private void collectDefinitions(ASTNode node) {
        switch (node.getName()) {
            case "tensor":
                registerTensor(node); // 注册张量定义
                break;
            case "node":
                registerNode(node); // 注册节点定义
                break;
            case "input_list":
            case "output_list":
                processIOPorts(node, true);  // 处理输入输出端口定义
                break;
            default:
                node.getChildren().forEach(this::collectDefinitions);// 递归处理子节点
        }
    }

    /**
     * 验证阶段：递归遍历AST，验证所有引用
     *
     * @param node 当前处理的AST节点
     */
    private void validateReferences(ASTNode node) {
        switch (node.getName()) {
            case "node":
                validateNode(node); // 验证节点语义
                break;
            default:
                node.getChildren().forEach(this::validateReferences); // 递归处理子节点
        }
    }

    /**
     * 注册张量：将张量信息添加到符号表，检查重复定义
     *
     * @param tensorNode 张量AST节点
     */
    private void registerTensor(ASTNode tensorNode) {
        String name = getChildValue(tensorNode, "name"); // 获取张量名称
        String dataType = getChildValue(tensorNode, "data_type").toUpperCase(); // 获取数据类型并转为大写
        int line = tensorNode.getLine(); // 获取行号
        int column = tensorNode.getColumn(); // 获取列号

        // 检查张量名称是否已存在（重复定义）
        if (tensorRegistry.containsKey(name)) {
            errors.add(new SemanticError(
                    "张量名称冲突: " + name, // 错误信息
                    line,
                    column
            ));
        }
        // 注册张量到符号表
        tensorRegistry.put(name, new TensorInfo(dataType, line, column));
    }

    /**
     * 注册节点：将节点信息添加到节点符号表，检查重复定义
     *
     * @param node 节点AST节点
     */
    private void registerNode(ASTNode node) {
        String nodeName = getChildValue(node, "name");
        String opType = getChildValue(node, "op_type").toUpperCase();
        int line = node.getLine();
        int column = node.getColumn();

        // 检查节点名称是否已存在（重复定义）
        if (nodeRegistry.containsKey(nodeName)) {
            errors.add(new SemanticError(
                    "节点名称冲突: " + nodeName,
                    line,
                    column
            ));
        }
        nodeRegistry.put(nodeName, new NodeInfo(opType, line, column));
    }

    /**
     * 处理输入输出端口：将IO端口信息添加到符号表
     *
     * @param ioList IO列表AST节点
     * @param isDefinition true表示是定义阶段，false表示是引用阶段
     */
    private void processIOPorts(ASTNode ioList, boolean isDefinition) {
        ioList.getChildren().stream()
                .filter(n -> "value_info".equals(n.getName())) // 过滤出value_info节点
                .forEach(valueInfo -> {
                    String tensorName = getChildValue(valueInfo, "name");
                    String dataType = parseTensorType(valueInfo);
                    int line = valueInfo.getLine();
                    int column = valueInfo.getColumn();

                    if (isDefinition) {
                        // 在定义阶段检查重复定义
                        if (tensorRegistry.containsKey(tensorName)) {
                            errors.add(new SemanticError(
                                    "IO端口名称冲突: " + tensorName,
                                    line,
                                    column
                            ));
                        }
                        tensorRegistry.put(tensorName, new TensorInfo(dataType, line, column));
                    }
                });
    }

    /**
     * 验证节点语义：检查节点输入输出的合法性和类型一致性
     *
     * @param node 节点AST节点
     */
    private void validateNode(ASTNode node) {
        String nodeName = getChildValue(node, "name");
        String opType = getChildValue(node, "op_type").toUpperCase();
        List<String> inputs = collectInputs(node);
        List<String> outputs = collectOutputs(node);
        int line = node.getLine();
        int column = node.getColumn();

        // 检查输入是否定义
        inputs.forEach(input -> {
            if (!tensorRegistry.containsKey(input)) {
                errors.add(new SemanticError(
                        "未定义的张量引用: " + input,
                        line,
                        column
                ));
            }
        });

        // 检查输出唯一性
        outputs.forEach(output -> {
            if (outputTensors.contains(output)) {
                errors.add(new SemanticError(
                        "输出张量冲突: " + output,
                        line,
                        column
                ));
            }
            outputTensors.add(output);
        });

        // 类型一致性检查
        TypeRule rule = OP_TYPE_RULES.get(opType);
        if (rule != null) {
            checkTypeCompatibility(node, inputs, outputs, rule);
        }
    }

    /**
     * 类型兼容性检查：验证输入类型是否符合操作要求，推断输出类型
     *
     * @param node 当前节点
     * @param inputs 输入张量列表
     * @param outputs 输出张量列表
     * @param rule 类型规则
     */
    private void checkTypeCompatibility(ASTNode node, List<String> inputs,
                                        List<String> outputs, TypeRule rule) {
        if (inputs.isEmpty()) return;

        // 检查输入类型
        String firstType = tensorRegistry.get(inputs.get(0)).dataType;
        boolean allMatch = inputs.stream()
                .map(input -> tensorRegistry.get(input).dataType)
                .allMatch(t -> t.equals(firstType));

        if (!allMatch) {
            errors.add(new SemanticError(
                    "操作 " + rule + " 的输入类型不一致",
                    node.getLine(),
                    node.getColumn()
            ));
        }

        // 检查是否允许的类型
        if (!rule.allowedInputTypes.contains(firstType)) {
            errors.add(new SemanticError(
                    "操作 " + rule + " 不支持输入类型: " + firstType,
                    node.getLine(),
                    node.getColumn()
            ));
        }

        // 推断输出类型并注册
        String outputType = rule.outputType.equals("INPUT0") ? firstType : rule.outputType;
        outputs.forEach(output -> {
            tensorRegistry.put(output, new TensorInfo(outputType, node.getLine(), node.getColumn()));
        });
    }

    /**
    * 模型级一致性检查：验证模型的整体语义正确性
    */
    private void checkModelConsistency() {
        // 检查所有输出张量都有定义
        outputTensors.forEach(output -> {
            if (!tensorRegistry.containsKey(output)) {
                errors.add(new SemanticError(
                        "输出张量未定义: " + output,
                        -1, // 全局错误
                        -1
                ));
            }
        });
    }

    // ============= 内部数据结构定义 =============

    /**
     * 张量信息：存储张量的类型和定义位置
     */
    private static class TensorInfo {
        String dataType;
        int definedLine;
        int definedColumn;

        TensorInfo(String dataType, int line, int column) {
            this.dataType = dataType;
            this.definedLine = line;
            this.definedColumn = column;
        }
    }

    /**
     * 节点信息：存储节点的操作类型和定义位置
     */
    private static class NodeInfo {
        String opType;
        int definedLine;
        int definedColumn;

        NodeInfo(String opType, int line, int column) {
            this.opType = opType;
            this.definedLine = line;
            this.definedColumn = column;
        }
    }

    /**
     * 类型规则：定义操作符的类型约束
     */
    private static class TypeRule {
        List<String> allowedInputTypes;
        String outputType;

        TypeRule(List<String> allowedInputTypes, String outputType) {
            this.allowedInputTypes = allowedInputTypes;
            this.outputType = outputType;
        }
    }

    // ============= 错误处理相关类 =============

    /**
     * 语义错误：包含错误信息和位置
     */
    public static class SemanticError {
        final String message; // 错误描述
        final int line;       // 错误发生行号（-1表示全局错误）
        final int column;     // 错误发生列号（-1表示全局错误）

        public SemanticError(String message, int line, int column) {
            this.message = message;
            this.line = line;
            this.column = column;
        }

        public String getMessage() { return message; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    /**
     * 语义错误异常：包含所有检测到的语义错误
     */
    public static class SemanticErrorException extends RuntimeException {
        private final List<SemanticError> errors;

        public SemanticErrorException(List<SemanticError> errors) {
            this.errors = errors;
        }

        public List<SemanticError> getErrors() { return errors; }
    }

    // ============= 辅助方法 =============
    // 以下辅助方法与之前相同，需添加行号信息

    /**
     * 获取AST节点的指定子节点的值
     */
    private String getChildValue(ASTNode parent, String childName) {
        return parent.getChildren().stream()
                .filter(n -> childName.equals(n.getName()))
                .findFirst()
                .map(ASTNode::getValue)
                .orElseThrow(() -> new RuntimeException("Missing " + childName));
    }

    /**
     * 收集节点的所有输入张量名称
     */
    private List<String> collectInputs(ASTNode node) {
        return node.getChildren().stream()
                .filter(n -> n.getName().endsWith("_list") || n.getName().endsWith("_arr"))
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "Value".equals(n.getName()))
                .map(ASTNode::getValue)
                .toList();
    }

    /**
     * 收集节点的所有输出张量名称
     */
    private List<String> collectOutputs(ASTNode node) {
        return node.getChildren().stream()
                .filter(n -> "output_list".equals(n.getName()) || "output_arr".equals(n.getName()))
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "Value".equals(n.getName()))
                .map(ASTNode::getValue)
                .toList();
    }

    /**
     * 解析张量类型：从value_info节点中提取数据类型
     */
    private String parseTensorType(ASTNode valueInfo) {
        return valueInfo.getChildren().stream()
                .filter(n -> "tensor_type".equals(n.getName()))
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "elem_type".equals(n.getName()))
                .findFirst()
                .map(ASTNode::getValue)
                .orElseThrow(() -> new RuntimeException("Missing elem_type"))
                .toUpperCase();
    }
}