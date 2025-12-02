package cxcx;

import java.io.IOException;
import java.util.List;

public class CompileMain {
    public static void main(String[] args) throws IOException {
        // 1. 读取文件并进行词法分析
        String filePath = "E:\\javawork\\compile\\src\\main\\java\\cx\\example7.txt";  // 输入文件路径
        LexicalAnalyzer lexer = new LexicalAnalyzer();
        String sourceCode = null;

        // 读取文件内容
        try {
            sourceCode = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 进行词法分析
        List<LexicalAnalyzer.Token> tokens = lexer.analyze(sourceCode);
        System.out.println("Lexical analysis results:");
        for (LexicalAnalyzer.Token token : tokens) {
            System.out.println(token.type + ": " + token.value);
        }

        // 2. 进行语法分析
        SyntaxAnalyzer syntaxAnalyzer = new SyntaxAnalyzer(tokens);
        ASTNode ast = syntaxAnalyzer.parse();
        System.out.println("\nSyntax analysis results (AST):");
        ast.print();

        try {
            // 检查并报告错误
            if (ast.hasErrors()) {
                System.out.println("\nErrors found:");
                for (SyntaxError error : ast.getErrors()) {
                    System.out.println(error.getMessage());
                }
            }
        } catch (SyntaxError e) {
            System.err.println("Fatal error: " + e.getMessage());
        }


        // 3. 进行语义分析
        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
        System.out.println("\nSemantic analysis results:");
        try {
            semanticAnalyzer.analyze(ast);
            System.out.println("未发现错误，语义检查通过");
        } catch (SemanticAnalyzer.SemanticErrorException e) {
            System.out.println("发现语义错误:");
            e.getErrors().forEach(err ->
                    System.out.printf("Line %d:%d - %s\n", err.line, err.column, err.message));
            System.out.println("发现 " + e.getErrors().size() + " 个错误");
        }

        // 4. 进行代码生成
        CodeGenerator codeGenerator = new CodeGenerator();
        System.out.println("\nCode generation results:");
        List<String> tacCode = codeGenerator.generate(ast);
        for (String line : tacCode) {
            System.out.println(line);
        }
    }
}
