构建S-ONNX模型的编译器S-ONNXCompiler
本实验项目将对ONNX模型进行裁剪，形成S-ONNX（Simplified Open Neural Network Exchange），保留了ONNX模型的核心结构和语义。
ONNX（Open Neural Network Exchange）是一种开放的神经网络模型交换格式，广泛应用于不同深度学习框架之间的模型共享和迁移。ONNX通过标准化的模型表示，使得开发者能够在TensorFlow、PyTorch、Caffe等多种框架之间无缝地传递和部署神经网络模型，其核心是计算图，包括节点（NodeProto）、张量（TensorProto）和操作符（Operator）。

完成以下任务：
1）依据S-ONNX的词法规则，把输入的S-ONNX源代码切分为词法单元，例如关键字、专用符号、标识符、常量等，为后续的语法分析提供基础；
2）根据S-ONNX的语法规则，对词法分析得到的词法单元序列进行分析，判断其是否符合S-ONNX的语法结构，并生成抽象语法树；
3）根据S-ONNX的语义要求，对S-ONNX模型进行语义检查；
4）在完成上述分析的基础上，将S-ONNX模型转换为三地址码表示的中间代码。


