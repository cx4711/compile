# 构建S-ONNX模型的编译器S-ONNXCompiler

## 项目简介
本实验项目将对ONNX模型进行裁剪，形成S-ONNX（Simplified Open Neural Network Exchange），保留了ONNX模型的核心结构和语义。

S-ONNXCompiler 是一款针对 S-ONNX 模型的编译器，实现了从 S-ONNX 源文件到三地址码中间表示的完整编译流程。本编译器支持词法分析、语法分析、语义分析和中间代码生成等核心功能，为模型验证和优化提供基础工具。

完成以下任务：

1）依据S-ONNX的词法规则，把输入的S-ONNX源代码切分为词法单元，例如关键字、专用符号、标识符、常量等，为后续的语法分析提供基础；

2）根据S-ONNX的语法规则，对词法分析得到的词法单元序列进行分析，判断其是否符合S-ONNX的语法结构，并生成抽象语法树；

3）根据S-ONNX的语义要求，对S-ONNX模型进行语义检查；

4）在完成上述分析的基础上，将S-ONNX模型转换为三地址码表示的中间代码。


## 主要特性
完整的编译流程：支持词法、语法、语义分析和中间代码生成

精准的错误定位：提供详细的错误信息和行列号定位

语义验证：支持名称唯一性、引用完整性和类型兼容性检查

中间代码生成：生成标准的三地址码中间表示

模块化设计：各编译阶段解耦，易于扩展和维护


## 技术栈
开发环境：IDEA 2023.2

编程语言：Java 17+

主要技术：正则表达式、递归下降解析、AST、符号表、三地址码


## 项目结构
```
S-ONNXCompiler/

├── src/main/java/com/compiler/

│   ├── CompileMain.java          # 主程序入口

│   ├── lexer/

│   │   ├── LexicalAnalyzer.java  # 词法分析器

│   │   ├── Token.java           # 词法单元类

│   │   └── TokenType.java       # 词法单元类型枚举

│   ├── parser/

│   │   ├── SyntaxAnalyzer.java  # 语法分析器

│   │   └── ast/                 # 抽象语法树相关类

│   ├── semantic/

│   │   ├── SemanticAnalyzer.java # 语义分析器

│   │   ├── SymbolTable.java     # 符号表

│   │   └── SemanticError.java   # 语义错误类

│   ├── codegen/

│   │   └── CodeGenerator.java   # 中间代码生成器

│   └── error/

│       └── CompilerError.java   # 错误处理基类

├── testcases/                   # 测试用例目录

│   ├── valid/                  # 有效测试用例

│   └── invalid/                # 无效测试用例（用于错误测试）

├── docs/                       # 文档目录

└── README.md                   # 本文件
```

## 快速开始
### 环境要求
Java 开发环境：Oracle OpenJDK 17.0.8 或更高版本

构建工具：Maven 或直接使用 IDE

IDE：推荐使用 IntelliJ IDEA 2023.2+

### 构建与运行
使用 IntelliJ IDEA 克隆项目到本地，

<<<<<<< HEAD
```
=======
```bash
>>>>>>> 3475e28078390fac44dfa94a96ec012f8f6b04be
git clone https://github.com/your-username/compile.git
```

下载完成后执行命令：`cd compile`

配置 JDK 17 运行环境

运行 CompileMain.java

## 使用说明
编译器支持命令行参数指定输入文件：

<<<<<<< HEAD
```
=======
```bash
>>>>>>> 3475e28078390fac44dfa94a96ec012f8f6b04be
java -jar S-ONNXCompiler.jar <input_file>
```

或通过主程序直接指定测试文件路径。

### 编译流程
词法分析：将 S-ONNX 源文件转换为 Token 序列

语法分析：验证语法结构并构建抽象语法树

语义分析：进行语义检查和类型验证

代码生成：生成三地址码中间表示

## 许可证
本项目采用 MIT 许可证 - 查看 LICENSE 文件了解详情。

