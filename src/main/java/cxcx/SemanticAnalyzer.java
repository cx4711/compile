package cxcx;

import java.util.*;

public class SemanticAnalyzer {
    // 错误收集结构
    private List<SemanticError> errors = new ArrayList<>();

    // 语义数据结构
    private final Map<String, TensorInfo> tensorRegistry = new HashMap<>(); // 张量名->类型+位置
    private final Map<String, NodeInfo> nodeRegistry = new HashMap<>();     // 节点名->操作类型+位置
    private final Set<String> outputTensors = new HashSet<>();

    // 类型兼容性规则
    private static final Map<String, TypeRule> OP_TYPE_RULES = new HashMap<>();
    static {
        OP_TYPE_RULES.put("Add", new TypeRule(List.of("FLOAT", "INT"), "INPUT0"));
        OP_TYPE_RULES.put("Conv", new TypeRule(List.of("FLOAT"), "FLOAT"));
        OP_TYPE_RULES.put("Reshape", new TypeRule(List.of("FLOAT", "INT"), "INPUT0"));
    }

    public void analyze(ASTNode modelAST) {
        errors.clear();
        collectDefinitions(modelAST);  // 第一遍：收集所有定义
        validateReferences(modelAST);  // 第二遍：验证引用和类型
        checkModelConsistency();       // 模型级检查

        if (!errors.isEmpty()) {
            throw new SemanticErrorException(errors);
        }
    }

    // 收集阶段：注册张量、节点
    private void collectDefinitions(ASTNode node) {
        switch (node.getName()) {
            case "tensor":
                registerTensor(node);
                break;
            case "node":
                registerNode(node);
                break;
            case "input_list":
            case "output_list":
                processIOPorts(node, true);
                break;
            default:
                node.getChildren().forEach(this::collectDefinitions);
        }
    }

    // 验证阶段：检查使用情况
    private void validateReferences(ASTNode node) {
        switch (node.getName()) {
            case "node":
                validateNode(node);
                break;
            default:
                node.getChildren().forEach(this::validateReferences);
        }
    }

    // 注册张量（带位置信息）
    private void registerTensor(ASTNode tensorNode) {
        String name = getChildValue(tensorNode, "name");
        String dataType = getChildValue(tensorNode, "data_type").toUpperCase();
        int line = tensorNode.getLine();
        int column = tensorNode.getColumn();

        if (tensorRegistry.containsKey(name)) {
            errors.add(new SemanticError(
                    "张量名称冲突: " + name,
                    line,
                    column
            ));
        }
        tensorRegistry.put(name, new TensorInfo(dataType, line, column));
    }

    // 注册节点
    private void registerNode(ASTNode node) {
        String nodeName = getChildValue(node, "name");
        String opType = getChildValue(node, "op_type").toUpperCase();
        int line = node.getLine();
        int column = node.getColumn();

        if (nodeRegistry.containsKey(nodeName)) {
            errors.add(new SemanticError(
                    "节点名称冲突: " + nodeName,
                    line,
                    column
            ));
        }
        nodeRegistry.put(nodeName, new NodeInfo(opType, line, column));
    }

    // 处理输入输出端口
    private void processIOPorts(ASTNode ioList, boolean isDefinition) {
        ioList.getChildren().stream()
                .filter(n -> "value_info".equals(n.getName()))
                .forEach(valueInfo -> {
                    String tensorName = getChildValue(valueInfo, "name");
                    String dataType = parseTensorType(valueInfo);
                    int line = valueInfo.getLine();
                    int column = valueInfo.getColumn();

                    if (isDefinition) {
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

    // 验证节点语义
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

    // 类型兼容性检查
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

    // 模型级一致性检查
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

    // 辅助数据结构
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

    private static class TypeRule {
        List<String> allowedInputTypes;
        String outputType;

        TypeRule(List<String> allowedInputTypes, String outputType) {
            this.allowedInputTypes = allowedInputTypes;
            this.outputType = outputType;
        }
    }

    // 错误定义
    public static class SemanticError {
        final String message;
        final int line;
        final int column;

        public SemanticError(String message, int line, int column) {
            this.message = message;
            this.line = line;
            this.column = column;
        }

        public String getMessage() { return message; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    public static class SemanticErrorException extends RuntimeException {
        private final List<SemanticError> errors;

        public SemanticErrorException(List<SemanticError> errors) {
            this.errors = errors;
        }

        public List<SemanticError> getErrors() { return errors; }
    }

    // 以下辅助方法与之前相同，需添加行号信息
    private String getChildValue(ASTNode parent, String childName) {
        return parent.getChildren().stream()
                .filter(n -> childName.equals(n.getName()))
                .findFirst()
                .map(ASTNode::getValue)
                .orElseThrow(() -> new RuntimeException("Missing " + childName));
    }

    private List<String> collectInputs(ASTNode node) {
        return node.getChildren().stream()
                .filter(n -> n.getName().endsWith("_list") || n.getName().endsWith("_arr"))
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "Value".equals(n.getName()))
                .map(ASTNode::getValue)
                .toList();
    }

    private List<String> collectOutputs(ASTNode node) {
        return node.getChildren().stream()
                .filter(n -> "output_list".equals(n.getName()) || "output_arr".equals(n.getName()))
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "Value".equals(n.getName()))
                .map(ASTNode::getValue)
                .toList();
    }

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