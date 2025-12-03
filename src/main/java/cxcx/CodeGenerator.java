package cxcx;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 三地址代码生成器 - 将AST转换为三地址代码(TAC)表示
 * 支持ONNX模型结构的转换，包括图、节点、张量等元素的处理
 */
public class CodeGenerator {
    private List<String> tacCode = new ArrayList<>();  // 存储生成的三地址代码
    private int tempCounter = 0;  // 临时变量计数器
    private int labelCounter = 0;  // 标签计数器（当前未使用）
    private Map<String, String> tensorToTempMap = new HashMap<>(); // 张量名到临时变量的映射
    private Map<String, String> tensorShapes = new HashMap<>();    // 张量形状信息

    /**
     * 主生成方法 - 遍历AST并生成三地址代码
     * @param ast 抽象语法树的根节点
     * @return 生成的三地址代码列表
     */
    public List<String> generate(ASTNode ast) {
        visitNode(ast);
        return tacCode;
    }

    /**
     * AST节点访问器 - 根据节点类型分派到不同的处理方法
     * @param node 当前访问的AST节点
     */
    private void visitNode(ASTNode node) {
        switch (node.getName()) {
            case "ModelBody":
                processModel(node);  // 处理模型定义
                break;
            case "graph":
                processGraph(node); // 处理计算图
                break;
            case "node":
                processNode(node); // 处理操作节点
                break;
            case "tensor":
                processTensor(node); // 处理张量定义
                break;
            case "opset_import":
                processOpsetImport(node);  // 处理算子集导入
                break;
            case "initializer_list":
                processInitializerList(node); // 处理初始化器列表
                break;
            case "input_list":
                processInputList(node);  // 处理输入列表
                break;
            case "output_list":
                processOutputList(node); // 处理输出列表
                break;
            case "opset_imports":
                processChildren(node);   // 处理算子集导入组
                break;
            default:
                processChildren(node); // 默认处理：递归处理子节点
                break;
        }
    }

    /**
     * 处理模型定义节点
     * 模型包含图和算子集导入两部分
     * @param modelNode 模型节点
     */
    private void processModel(ASTNode modelNode) {
        tacCode.add("; MODEL DEFINITION");  // 添加模型定义注释
        ASTNode graphNode = null;
        ASTNode opsetImportsNode = null;

        // 分离graph和opset_imports节点
        for (ASTNode child : modelNode.getChildren()) {
            if ("graph".equals(child.getName())) {
                graphNode = child;
            } else if ("opset_imports".equals(child.getName())) {
                opsetImportsNode = child;
            }
        }

        // 先处理graph
        if (graphNode != null) {
            processGraph(graphNode);
        }

        // 最后处理opset_imports
        if (opsetImportsNode != null) {
            processChildren(opsetImportsNode);
        }
    }


    /**
     * 处理计算图节点
     * @param graphNode 图节点
     */
    private void processGraph(ASTNode graphNode) {
        String graphName = getGraphName(graphNode);  // 获取图名称
        tacCode.add(String.format("; GRAPH: %s", graphName));  // 添加图注释

        // 查找graph_body节点
        ASTNode graphBody = null;
        for (ASTNode child : graphNode.getChildren()) {
            if ("graph_body".equals(child.getName())) {
                graphBody = child;
                break;
            }
        }

        if (graphBody != null) {
            // 显式控制处理顺序
            List<ASTNode> inputLists = new ArrayList<>();
            ASTNode initializerList = null;
            List<ASTNode> nodeLists = new ArrayList<>();
            ASTNode outputList = null;

            // 分类子节点
            for (ASTNode child : graphBody.getChildren()) {
                if ("input_list".equals(child.getName())) {
                    inputLists.add(child);
                } else if ("initializer_list".equals(child.getName())) {
                    initializerList = child;
                } else if ("node_list".equals(child.getName())) {
                    nodeLists.add(child);
                } else if ("output_list".equals(child.getName())) {
                    outputList = child;
                }
            }

            // 按顺序处理
            for (ASTNode inputList : inputLists) {
                processInputList(inputList);
            }

            if (initializerList != null) {
                processInitializerList(initializerList);
            }

            for (ASTNode nodeList : nodeLists) {
                processNodeList(nodeList);
            }

            if (outputList != null) {
                processOutputList(outputList);
            }
        }
    }

    /**
     * 处理节点列表
     * @param nodeList 节点列表
     */
    private void processNodeList(ASTNode nodeList) {
        for (ASTNode node : nodeList.getChildren()) {
            if ("node".equals(node.getName())) {
                processNode(node);
            }
        }
    }

    /**
     * 处理输入列表 - 生成INPUT指令
     * @param inputList 输入列表节点
     */
    private void processInputList(ASTNode inputList) {
        for (ASTNode input : inputList.getChildren()) {
            if ("value_info".equals(input.getName())) {
                String name = "";
                String dataType = "";
                List<String> dims = new ArrayList<>();

                // 提取输入信息：名称、数据类型、维度
                for (ASTNode field : input.getChildren()) {
                    if ("name".equals(field.getName())) {
                        name = field.getValue().replace("\"", "");
                    } else if ("tensor_type".equals(field.getName())) {
                        for (ASTNode child : field.getChildren()) {
                            if ("elem_type".equals(child.getName())) {
                                dataType = child.getValue().toUpperCase();
                            } else if ("shape".equals(child.getName())) {
                                dims = extractDims(child);
                            }
                        }
                    }
                }

                // 生成输入指令
                if (!name.isEmpty() && !dataType.isEmpty()) {
                    String temp = newTemp();
                    tensorToTempMap.put(name, temp); // 建立映射
                    String shape = dims.isEmpty() ? "" : "[" + String.join(",", dims) + "]";
                    tensorShapes.put(name, shape);  // 存储形状
                    tacCode.add(String.format("%s = INPUT(\"%s\", %s, %s)",
                            temp, name, dataType, shape));
                }
            }
        }
    }

    /**
     * 处理输出列表 - 生成OUTPUT指令
     * @param outputList 输出列表节点
     */
    private void processOutputList(ASTNode outputList) {
        for (ASTNode output : outputList.getChildren()) {
            if ("value_info".equals(output.getName())) {
                String name = "";

                // 提取输出名称
                for (ASTNode field : output.getChildren()) {
                    if ("name".equals(field.getName())) {
                        name = field.getValue().replace("\"", "");
                    }
                }

                // 生成输出指令
                if (!name.isEmpty()) {
                    String temp = tensorToTempMap.getOrDefault(name, name);
                    tacCode.add(String.format("OUTPUT(\"%s\", %s)", name, temp));
                }
            }
        }
    }

    /**
     * 处理操作节点 - 生成对应的三地址指令
     * @param node 操作节点
     */
    private void processNode(ASTNode node) {
        String opType = "";
        String nodeName = "";
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        Map<String, String> attributes = new HashMap<>();

        // 提取节点信息
        for (ASTNode child : node.getChildren()) {
            switch (child.getName()) {
                case "op_type":
                    opType = child.getValue().replace("\"", "").toUpperCase();
                    break;
                case "name":
                    nodeName = child.getValue().replace("\"", "");
                    break;
                case "input_list":
                case "input_arr":
                    inputs.addAll(extractValues(child));
                    break;
                case "output_list":
                case "output_arr":
                    outputs.addAll(extractValues(child));
                    break;
                case "attribute_list":
                    attributes.putAll(extractAttributes(child));
                    break;
            }
        }

        // 生成操作指令
        if (!opType.isEmpty()) {
            List<String> outputTemps = new ArrayList<>();
            // 为每个输出创建临时变量
            for (String output : outputs) {
                String temp = newTemp();
                tensorToTempMap.put(output, temp);
                outputTemps.add(temp);
            }

            // 获取输入对应的临时变量
            List<String> inputTemps = inputs.stream()
                    .map(input -> tensorToTempMap.getOrDefault(input, input))
                    .collect(Collectors.toList());

            // 格式化属性字符串
            String attrString = attributes.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));

            // 生成指令字符串
            String operation;
            if (outputTemps.size() == 1) {
                operation = String.format("%s = %s(%s%s)",
                        outputTemps.get(0), opType,
                        String.join(", ", inputTemps),
                        attrString.isEmpty() ? "" : ", " + attrString);
            } else {
                operation = String.format("%s = %s(%s%s)",
                        String.join(", ", outputTemps), opType,
                        String.join(", ", inputTemps),
                        attrString.isEmpty() ? "" : ", " + attrString);
            }

            // 添加节点名称作为注释
            if (!nodeName.isEmpty()) {
                operation += " ; " + nodeName;
            }

            tacCode.add(operation);
        }
        // 移除processChildren(node)调用
    }

    /**
     * 处理张量定义 - 生成INITIALIZER指令
     * @param tensorNode 张量节点
     */
    private void processTensor(ASTNode tensorNode) {
        String tensorName = "";
        String dataType = "";
        List<String> dims = new ArrayList<>();
        String rawData = "";

        // 提取张量信息
        for (ASTNode child : tensorNode.getChildren()) {
            switch (child.getName()) {
                case "name":
                    tensorName = child.getValue().replace("\"", "");
                    break;
                case "data_type":
                    dataType = child.getValue().toUpperCase();
                    break;
                case "dims":
                    dims = extractDims(child);
                    break;
                case "raw_data":
                    rawData = child.getValue();
                    break;
            }
        }

        // 生成初始化器指令
        if (!tensorName.isEmpty()) {
            String temp = newTemp();
            tensorToTempMap.put(tensorName, temp);
            String shape = dims.isEmpty() ? "" : "[" + String.join(",", dims) + "]";
            tensorShapes.put(tensorName, shape);
            tacCode.add(String.format("%s = INITIALIZER(\"%s\", %s, %s, \"%s\")",
                    temp, tensorName, dataType, shape, rawData));
        }
    }

    /**
     * 处理常量节点（示例方法）
     * @param constantNode 常量节点
     */
    private void processConstant(ASTNode constantNode) {
        String value = constantNode.getValue();
        String dataType = "FLOAT"; // 根据实际情况获取
        String shape = "[1]";      // 根据实际情况获取

        String temp = newTemp();
        tacCode.add(String.format("%s = CONSTANT(%s, %s, %s)",
                temp, value, dataType, shape));
    }

    /**
     * 处理Reshape操作（示例方法）
     * @param reshapeNode Reshape节点
     */
    private void processReshape(ASTNode reshapeNode) {
        String input = tensorToTempMap.get(reshapeNode.getValue());
        List<String> newShape = Arrays.asList("1", "64", "112", "112"); // 根据实际情况获取

        String temp = newTemp();
        tacCode.add(String.format("%s = RESHAPE(%s, [%s])",
                temp, input, String.join(",", newShape)));
    }

    /**
     * 提取节点属性
     * @param attributeList 属性列表节点
     * @return 属性名到属性值的映射
     */
    private Map<String, String> extractAttributes(ASTNode attributeList) {
        Map<String, String> attributes = new HashMap<>();
        for (ASTNode attr : attributeList.getChildren()) {
            if ("attribute".equals(attr.getName())) {
                String name = "";
                String value = "";
                for (ASTNode attrChild : attr.getChildren()) {
                    if ("name".equals(attrChild.getName())) {
                        name = attrChild.getValue().replace("\"", "");
                    } else if ("Value".equals(attrChild.getName())) {
                        value = attrChild.getValue().replace("\"", "");
                    }
                }
                if (!name.isEmpty() && !value.isEmpty()) {
                    attributes.put(name, value);
                }
            }
        }
        return attributes;
    }

    /**
     * 提取张量维度信息
     * @param dimsNode 维度节点
     * @return 维度值列表
     */
    private List<String> extractDims(ASTNode dimsNode) {
        List<String> dims = new ArrayList<>();
        for (ASTNode child : dimsNode.getChildren()) {
            // 情况1：直接处理 dim_value 或 dim_param
            if ("dim_value".equals(child.getName()) || "dim_param".equals(child.getName())) {
                dims.add(child.getValue());
                continue;
            }

            // 情况2：处理带 dim_list 的结构
            if ("dim_list".equals(child.getName())) {
                for (ASTNode dim : child.getChildren()) {
                    if ("dim".equals(dim.getName())) {
                        for (ASTNode dimChild : dim.getChildren()) {
                            if ("dim_value".equals(dimChild.getName())) {
                                dims.add(dimChild.getValue());
                            }else if ("dim_param".equals(dimChild.getName())) {
                                dims.add(dimChild.getValue());
                            }
                        }
                    }
                }
            }
        }
        return dims;
    }

    /**
     * 提取值列表
     * @param container 包含Value节点的容器
     * @return 值字符串列表
     */
    private List<String> extractValues(ASTNode container) {
        return container.getChildren().stream()
                .filter(child -> "Value".equals(child.getName()))
                .map(ASTNode::getValue)
                .map(value -> value.replace("\"", ""))
                .collect(Collectors.toList());
    }

    /**
     * 获取计算图名称
     * @param graphNode 图节点
     * @return 图名称，如果未找到则返回"unnamed_graph"
     */
    private String getGraphName(ASTNode graphNode) {
        for (ASTNode child : graphNode.getChildren()) {
            if ("graph_body".equals(child.getName())) {
                for (ASTNode subChild : child.getChildren()) {
                    if ("name".equals(subChild.getName())) {
                        return subChild.getValue().replace("\"", "");
                    }
                }
            }
        }
        return "unnamed_graph";
    }

    /**
     * 处理算子集导入信息
     * @param opsetNode 算子集节点
     */
    private void processOpsetImport(ASTNode opsetNode) {
        String domain = "";
        String version = "";

        for (ASTNode child : opsetNode.getChildren()) {
            if ("domain".equals(child.getName())) {
                domain = child.getValue().replace("\"", "");
            } else if ("version".equals(child.getName())) {
                version = child.getValue();
            }
        }

        if (!domain.isEmpty() && !version.isEmpty()) {
            tacCode.add(String.format("; OPSET %s VERSION %s", domain, version));
        }
    }

    /**
     * 处理初始化器列表
     * @param initializerList 初始化器列表节点
     */
    private void processInitializerList(ASTNode initializerList) {
        for (ASTNode child : initializerList.getChildren()) {
            if ("tensor".equals(child.getName())) {
                processTensor(child);
            }
        }
    }

    /**
     * 递归处理所有子节点
     * @param node 当前节点
     */
    private void processChildren(ASTNode node) {
        for (ASTNode child : node.getChildren()) {
            visitNode(child);
        }
    }

    /**
     * 生成新的临时变量名
     * @return 临时变量名（格式：t + 计数器）
     */
    private String newTemp() {
        String temp = "t" + tempCounter++;
        return temp;
    }
}