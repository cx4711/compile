package cxcx;

import java.util.*;
import java.util.stream.Collectors;

public class CodeGenerator {
    private List<String> tacCode = new ArrayList<>();
    private int tempCounter = 0;
    private int labelCounter = 0;
    private Map<String, String> tensorToTempMap = new HashMap<>(); // 张量名到临时变量的映射
    private Map<String, String> tensorShapes = new HashMap<>();    // 张量形状信息

    public List<String> generate(ASTNode ast) {
        visitNode(ast);
        return tacCode;
    }

    private void visitNode(ASTNode node) {
        switch (node.getName()) {
            case "ModelBody":
                processModel(node);
                break;
            case "graph":
                processGraph(node);
                break;
            case "node":
                processNode(node);
                break;
            case "tensor":
                processTensor(node);
                break;
            case "opset_import":
                processOpsetImport(node);
                break;
            case "initializer_list":
                processInitializerList(node);
                break;
            case "input_list":
                processInputList(node);
                break;
            case "output_list":
                processOutputList(node);
                break;
            case "opset_imports":  // 添加对opset_imports的处理
                processChildren(node);
                break;
            default:
                processChildren(node);
                break;
        }
    }

    private void processModel(ASTNode modelNode) {
        tacCode.add("; MODEL DEFINITION");
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


    private void processGraph(ASTNode graphNode) {
        String graphName = getGraphName(graphNode);
        tacCode.add(String.format("; GRAPH: %s", graphName));

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

    private void processNodeList(ASTNode nodeList) {
        for (ASTNode node : nodeList.getChildren()) {
            if ("node".equals(node.getName())) {
                processNode(node);
            }
        }
    }

    // 处理输入列表，生成Input指令
    private void processInputList(ASTNode inputList) {
        for (ASTNode input : inputList.getChildren()) {
            if ("value_info".equals(input.getName())) {
                String name = "";
                String dataType = "";
                List<String> dims = new ArrayList<>();

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

                if (!name.isEmpty() && !dataType.isEmpty()) {
                    String temp = newTemp();
                    tensorToTempMap.put(name, temp);
                    String shape = dims.isEmpty() ? "" : "[" + String.join(",", dims) + "]";
                    tensorShapes.put(name, shape);
                    tacCode.add(String.format("%s = INPUT(\"%s\", %s, %s)",
                            temp, name, dataType, shape));
                }
            }
        }
    }

    // 处理输出列表，生成Output指令
    private void processOutputList(ASTNode outputList) {
        for (ASTNode output : outputList.getChildren()) {
            if ("value_info".equals(output.getName())) {
                String name = "";
                for (ASTNode field : output.getChildren()) {
                    if ("name".equals(field.getName())) {
                        name = field.getValue().replace("\"", "");
                    }
                }

                if (!name.isEmpty()) {
                    String temp = tensorToTempMap.getOrDefault(name, name);
                    tacCode.add(String.format("OUTPUT(\"%s\", %s)", name, temp));
                }
            }
        }
    }

    // 处理节点操作
    private void processNode(ASTNode node) {
        String opType = "";
        String nodeName = "";
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        Map<String, String> attributes = new HashMap<>();

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

        if (!opType.isEmpty()) {
            List<String> outputTemps = new ArrayList<>();
            for (String output : outputs) {
                String temp = newTemp();
                tensorToTempMap.put(output, temp);
                outputTemps.add(temp);
            }

            List<String> inputTemps = inputs.stream()
                    .map(input -> tensorToTempMap.getOrDefault(input, input))
                    .collect(Collectors.toList());

            String attrString = attributes.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));

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

            if (!nodeName.isEmpty()) {
                operation += " ; " + nodeName;
            }

            tacCode.add(operation);
        }
        // 移除processChildren(node)调用
    }

    // 处理初始化器
    private void processTensor(ASTNode tensorNode) {
        String tensorName = "";
        String dataType = "";
        List<String> dims = new ArrayList<>();
        String rawData = "";

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

        if (!tensorName.isEmpty()) {
            String temp = newTemp();
            tensorToTempMap.put(tensorName, temp);
            String shape = dims.isEmpty() ? "" : "[" + String.join(",", dims) + "]";
            tensorShapes.put(tensorName, shape);
            tacCode.add(String.format("%s = INITIALIZER(\"%s\", %s, %s, \"%s\")",
                    temp, tensorName, dataType, shape, rawData));
        }
    }

    // 处理常量（示例）
    private void processConstant(ASTNode constantNode) {
        String value = constantNode.getValue();
        String dataType = "FLOAT"; // 根据实际情况获取
        String shape = "[1]";      // 根据实际情况获取

        String temp = newTemp();
        tacCode.add(String.format("%s = CONSTANT(%s, %s, %s)",
                temp, value, dataType, shape));
    }

    // 处理Reshape操作（示例）
    private void processReshape(ASTNode reshapeNode) {
        String input = tensorToTempMap.get(reshapeNode.getValue());
        List<String> newShape = Arrays.asList("1", "64", "112", "112"); // 根据实际情况获取

        String temp = newTemp();
        tacCode.add(String.format("%s = RESHAPE(%s, [%s])",
                temp, input, String.join(",", newShape)));
    }

    // 提取属性
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

    // 提取维度
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

    // 提取值
    private List<String> extractValues(ASTNode container) {
        return container.getChildren().stream()
                .filter(child -> "Value".equals(child.getName()))
                .map(ASTNode::getValue)
                .map(value -> value.replace("\"", ""))
                .collect(Collectors.toList());
    }

    // 获取图名称
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

    private void processInitializerList(ASTNode initializerList) {
        for (ASTNode child : initializerList.getChildren()) {
            if ("tensor".equals(child.getName())) {
                processTensor(child);
            }
        }
    }

    private void processChildren(ASTNode node) {
        for (ASTNode child : node.getChildren()) {
            visitNode(child);
        }
    }

    // 生成新的临时变量
    private String newTemp() {
        String temp = "t" + tempCounter++;
        return temp;
    }
}