package wasm;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Compiler {

    private Map<String, Integer> symbols = new HashMap<>();

    //// https://webassembly.github.io/spec/core/binary/modules.html#sections
    private enum Section {
        custom(0),
        type_section(1),
        import_section(2),
        func(3),
        table(4),
        memory(5),
        global(6),
        export(7),
        start(8),
        element(9),
        code(10),
        data(11);
        private final byte index;

        Section(int index) {
            this.index = (byte) index;
        }
    }

    // https://webassembly.github.io/spec/core/binary/types.html
    private enum Valtype {
        i32((byte) 0x7f),
        f32((byte) 0x7d);
        private final byte val;
        Valtype(byte val) {
            this.val = val;
        }
    }

    // https://webassembly.github.io/spec/core/binary/types.html#binary-blocktype
    private enum Blocktype {
        void_block((byte)0x40);
        private final byte val;
        Blocktype(byte val) {
            this.val = val;
        }
    }

    // https://webassembly.github.io/spec/core/binary/instructions.html
    private enum Opcodes {
        block((byte) 0x02),
        loop((byte) 0x03),
        br((byte) 0x0c),
        br_if((byte) 0x0d),
        end((byte) 0x0b),
        call((byte) 0x10),
        get_local((byte) 0x20),
        set_local((byte) 0x21),
        i32_store_8((byte) 0x3a),
        i32_const((byte) 0x41),
        f32_const((byte) 0x43),
        i32_eqz((byte) 0x45),
        i32_eq((byte) 0x46),
        f32_eq((byte) 0x5b),
        f32_lt((byte) 0x5d),
        f32_gt((byte) 0x5e),
        i32_and((byte) 0x71),
        f32_add((byte) 0x92),
        f32_sub((byte) 0x93),
        f32_mul((byte) 0x94),
        f32_div((byte) 0x95),
        i32_trunc_f32_s((byte) 0xa8);
        private final byte val;
        Opcodes(byte val) {
            this.val = val;
        }
    }

    private Map<String, Opcodes> binaryOpcode = Map.of("+", Opcodes.f32_add, "-", Opcodes.f32_sub,
            "*", Opcodes.f32_mul, "/", Opcodes.f32_div, "==", Opcodes.f32_eq,
            ">", Opcodes.f32_gt, "<", Opcodes.f32_lt, "&&", Opcodes.i32_and);

    // http://webassembly.github.io/spec/core/binary/modules.html#export-section
    private enum ExportType {
        func((byte) 0x00),
        table((byte) 0x01),
        mem((byte) 0x02),
        global((byte) 0x03);
        private final byte val;
        ExportType(byte val) {
            this.val = val;
        }
    }

    // http://webassembly.github.io/spec/core/binary/types.html#function-types
    private byte functionType = 0x60;
    private byte emptyArray = 0x0;
    private byte[] magicModuleHeader = new byte[]{0x00, 0x61, 0x73, 0x6d};
    private byte[] moduleVersion = new byte[]{0x01, 0x00, 0x00, 0x00};

    // https://webassembly.github.io/spec/core/binary/conventions.html#binary-vec
    // Vectors are encoded with their length followed by their element sequence
    private byte[] encodeVector(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(Leb128.writeUnsignedLeb128(data.length));
        baos.write(data);
        return baos.toByteArray();
    }
    private byte[] encodeVector(int len, byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(Leb128.writeUnsignedLeb128(len));
        baos.write(data);
        return baos.toByteArray();
    }
    private byte[] append(byte[] str, byte[] b, byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(str);
        baos.write(b);
        baos.write(data);
        return baos.toByteArray();
    }
    private byte[] append(byte[] a, byte[] b) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(a);
            baos.write(b);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
    // https://webassembly.github.io/spec/core/binary/modules.html#code-section
    byte[] encodeLocal(int count, Valtype type) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(Leb128.writeUnsignedLeb128(count));
        }catch(Exception e){
            throw new IllegalArgumentException(e);
        }
        baos.write(type.val);
        return baos.toByteArray();
    }


    // https://webassembly.github.io/spec/core/binary/modules.html#sections
    // sections are encoded by their type followed by their vector contents
    private byte[] createSection(byte sectionType, byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(sectionType);
        baos.write(encodeVector(data));
        return baos.toByteArray();
    }

    // Function types are vectors of parameters and return types. Currently
    // WebAssembly only supports single return values
    private byte[] addFunctionType() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(functionType);
        byte[] params = new byte[] { Valtype.f32.val, Valtype.f32.val};
        baos.write(encodeVector(params));
        byte[] ret = new byte[] { Valtype.f32.val};
        baos.write(encodeVector(ret));
        return baos.toByteArray();
    }
    private byte[] voidVoidType() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(functionType);
        baos.write(emptyArray);
        baos.write(emptyArray);
        return baos.toByteArray();
    }
    private byte[] floatVoidType() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(functionType);
        byte[] ret = new byte[] { Valtype.f32.val};
        baos.write(encodeVector(ret));
        baos.write(emptyArray);
        return baos.toByteArray();
    }
    //https://stackoverflow.com/a/3523066
    private static void reverse(byte[] data) {
        int left = 0;
        int right = data.length - 1;
        while( left < right ) {
            // swap the values at the left and right indices
            byte temp = data[left];
            data[left] = data[right];
            data[right] = temp;
            // move the left and right index pointers in toward the center
            left++;
            right--;
        }
    }
    private byte[] ieee754(float fl) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeFloat(fl);
            dos.close();
            byte[] bytes = baos.toByteArray();
            reverse(bytes);
            return bytes;
        }catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private byte[] encodeString(String str) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(str.length());
        for(int i=0; i < str.length(); i++) {
            baos.write(str.charAt(i));
        }
        return baos.toByteArray();
    }
    private byte[] emitExpression(Parser.ExpressionNode expressionNode, ByteArrayOutputStream baos) {
        Consumer<Parser.ExpressionNode> visitor = node -> {
            try {
                if (node.type.equals("numberLiteral")) {
                    baos.write(Opcodes.f32_const.val);
                    baos.write(ieee754(Float.valueOf(node.value)));
                } else if (node.type.equals("identifier")) {
                    baos.write(Opcodes.get_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol(node.value)));
                } else if (node.type.equals("binaryExpression")) {
                    baos.write(binaryOpcode.get(node.value).val);
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        traverse(List.of(expressionNode), visitor);
        return baos.toByteArray();
    }
    private void traverse(List<Parser.ExpressionNode> nodes, Consumer<Parser.ExpressionNode> visitor) {
        // post order ast walker
        for(Parser.ExpressionNode expression : nodes) {
            if (expression instanceof Parser.BinaryExpressionNode) {
                Parser.BinaryExpressionNode node = (Parser.BinaryExpressionNode)expression;
                traverse(List.of(node.left), visitor);
                traverse(List.of(node.right), visitor);
            }
            visitor.accept(expression);
        }
    }
    private int localIndexForSymbol(String name) {
        if (!symbols.containsKey(name)) {
            symbols.put(name, symbols.size());
        }
        return symbols.get(name);
    }

    private byte[] codeFromAst(List<Parser.StatementNode> ast) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        emitStatements(ast, baos);
        return baos.toByteArray();
    }
    private byte[] emitStatements(List<Parser.StatementNode> nodes, ByteArrayOutputStream baos) {
        try {
            for(Parser.StatementNode node : nodes) {
                String type = node.type;
                if(type.equals("printStatement")) {
                    emitExpression(node.value, baos);
                    baos.write(Opcodes.call.val);
                    baos.write(Leb128.writeUnsignedLeb128(0));
                } else if (type.equals("variableDeclaration")) {
                    Parser.VariableDeclarationNode var = (Parser.VariableDeclarationNode) node;
                    emitExpression(var.value, baos);
                    baos.write(Opcodes.set_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol(var.name)));
                } else if (type.equals("variableAssignment")) {
                    Parser.VariableAssignmentNode assignmentNode = (Parser.VariableAssignmentNode) node;
                    emitExpression(assignmentNode.value, baos);
                    baos.write(Opcodes.set_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol(assignmentNode.name)));
                } else if (type.equals("whileStatement")) {
                    Parser.WhileStatementNode whileNode = (Parser.WhileStatementNode) node;
                    // outer block
                    baos.write(Opcodes.block.val);
                    baos.write(Blocktype.void_block.val);
                    // inner loop
                    baos.write(Opcodes.loop.val);
                    baos.write(Blocktype.void_block.val);

                    // compute the while expression
                    emitExpression(whileNode.value, baos);
                    baos.write(Opcodes.i32_eqz.val);
                    // br_if $label0
                    baos.write(Opcodes.br_if.val);
                    baos.write(Leb128.writeSignedLeb128(1));
                    // the nested logic
                    emitStatements(whileNode.statements, baos);
                    // br $label1
                    baos.write(Opcodes.br.val);
                    baos.write(Leb128.writeSignedLeb128(0));
                    // end loop
                    baos.write(Opcodes.end.val);
                    // end block
                    baos.write(Opcodes.end.val);
                } else if (type.equals("ifStatement")) {
                    Parser.IfStatementNode ifNode = (Parser.IfStatementNode) node;
                    // if block
                    baos.write(Opcodes.block.val);
                    baos.write(Blocktype.void_block.val);
                    // compute the if expression
                    emitExpression(ifNode.value, baos);
                    baos.write(Opcodes.i32_eqz.val);
                    // br_if $label0
                    baos.write(Opcodes.br_if.val);
                    baos.write(Leb128.writeSignedLeb128(0));
                    // the nested logic
                    emitStatements(ifNode.consequent, baos);
                    // end block
                    baos.write(Opcodes.end.val);

                    // else block
                    baos.write(Opcodes.block.val);
                    baos.write(Blocktype.void_block.val);
                    // compute the if expression
                    emitExpression(ifNode.value, baos);
                    baos.write(Opcodes.i32_const.val);
                    baos.write(Leb128.writeSignedLeb128(1));
                    baos.write(Opcodes.i32_eq.val);
                    // br_if $label0
                    baos.write(Opcodes.br_if.val);
                    baos.write(Leb128.writeSignedLeb128(0));
                    // the nested logic
                    emitStatements(ifNode.alternate, baos);
                    // end block
                    baos.write(Opcodes.end.val);
                } else if (type.equals("setpixelStatement")) {
                    Parser.SetPixelStatementNode setPixelNode = (Parser.SetPixelStatementNode) node;
                    // compute and cache the setpixel parameters
                    emitExpression(setPixelNode.x, baos);
                    baos.write(Opcodes.set_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("x")));
                    emitExpression(setPixelNode.y, baos);
                    baos.write(Opcodes.set_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("y")));
                    emitExpression(setPixelNode.value, baos);
                    baos.write(Opcodes.set_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("color")));
                    // compute the offset (x * 100) + y
                    baos.write(Opcodes.get_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("y")));
                    baos.write(Opcodes.f32_const.val);
                    baos.write(ieee754(Float.valueOf(100)));
                    baos.write(Opcodes.f32_mul.val);
                    baos.write(Opcodes.get_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("x")));
                    baos.write(Opcodes.f32_add.val);
                    // convert to an integer
                    baos.write(Opcodes.i32_trunc_f32_s.val);
                    // fetch the color
                    baos.write(Opcodes.get_local.val);
                    baos.write(Leb128.writeUnsignedLeb128(localIndexForSymbol("color")));
                    baos.write(Opcodes.i32_trunc_f32_s.val);
                    // write
                    baos.write(Opcodes.i32_store_8.val);
                    baos.write(new byte[] {0x00, 0x00}); // align and offset
                }
            }
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
        return baos.toByteArray();
    }

    public Compiler() {
        try {
            //String input = "print 8";
            //4 String input = "print ( 2 + 4 )";
            //5 String input = "var f = 22 print f";
            //6.1 String input = "var f = 3 f = ( f + -1 ) print f";
            //6.2 String input = "var f = 0 while ( f < 5 ) f = ( f + 1 ) print f endwhile";
            //7 String input = "setpixel 1 2 240";
            //8 String input = "if ( 5 > 3 ) print 2 else print 3 endif";
            //8.2 String input = "if ( 5 < 3 ) print 2 else print 3 endif";
            //String input = "var far = 22 far = ( far + 1 ) print far";
            String input = "" +
                    " var y  = 0 " +
                    " while ( y < 100 ) " +
                    "   y = ( y + 1 ) " +
                    "   var x  = 0 " +
                    "   while ( x < 100 ) " +
                    "       x = ( x + 1 ) " +
                    "       var e = ( ( y / 50 ) - 1.5 ) " +
                    "       var f = ( ( x / 50 ) - 1 ) " +
                    "       var a = 0 " +
                    "       var b = 0 " +
                    "       var i = 0 " +
                    "       var j = 0 " +
                    "       var c = 0 " +
                    "       while ( ( ( ( i * i ) + ( j * j ) ) < 4 ) && ( c < 255 ) ) " +
                    "           i = ( ( ( a * a ) - ( b * b ) ) + e ) " +
                    "           j = ( ( ( 2 * a ) * b ) + f ) " +
                    "           a = i " +
                    "           b = j " +
                    "           c = ( c + 1 ) " +
                    "       endwhile " +
                    "       setpixel x y c " +
                    "   endwhile " +
                    " endwhile ";
            input = input.trim().replaceAll(" +", " ");
            input = input.replaceAll("\t", "");
            String filename = "generated-fractal.wasm";
            Tokenizer tokenizer = new Tokenizer();
            List<Tokenizer.Token> tokens = tokenizer.tokenize(input);
            List<Parser.StatementNode> ast = Parser.parse(tokens);
            byte[] contents = build(ast);
            writeFile(contents, filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void writeFile(byte[] contents, String filename) {
        File file = new File(filename);
        try {
            Files.write(file.toPath(), contents);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private byte[] build(List<Parser.StatementNode> ast) throws Exception {
        // the type section is a vector of function types
        byte[] typeSection = createSection(Section.type_section.index
                , encodeVector(2, append(voidVoidType(), floatVoidType())));

        // the function section is a vector of type indices that indicate the type of each function
        // in the code section
        byte[] funcSection = createSection(Section.func.index, encodeVector(new byte[] { 0x00 /* type index */}));

        //the import section is a vector of imported functions
        byte[] printFunctionImport = append(append(encodeString("env"), encodeString("print"))
                , new byte[] {ExportType.func.val}, new byte[] { 0x01 /* type index */});

        byte[] memoryImport = append(append(encodeString("env"), encodeString("memory"))
                , new byte[] {ExportType.mem.val}, new byte[] { 0x00, 0x01 // limits https://webassembly.github.io/spec/core/binary/types.html#limits - indicates a min memory size of one page
        });
        ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
        baos3.write(printFunctionImport);
        baos3.write(memoryImport);
        byte[] importSection = createSection(Section.import_section.index, encodeVector(2, baos3.toByteArray()));

        byte[] exportSection = createSection(Section.export.index,
                encodeVector(1, append(
                        encodeString("run")
                        , new byte[] {ExportType.func.val}
                        , new byte[] { 0x01 /* function index */})
                )
        );
        // the code section contains vectors of functions
        byte[] code = codeFromAst(ast);
        byte[] locals = new byte[0];
        byte[] functionBody = null;
        if( symbols.size() > 0 ) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(encodeLocal(symbols.size(), Valtype.f32));
            locals = encodeVector(1, baos.toByteArray());
            functionBody = encodeVector(append(locals, code, new byte[] {Opcodes.end.val}));
        } else {
            functionBody = encodeVector(append(new byte[] {emptyArray /** locals */}, code, new byte[] {Opcodes.end.val}));
        }

        byte[] codeSection = createSection(Section.code.index, encodeVector(1, functionBody));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(magicModuleHeader);
        baos.write(moduleVersion);
        baos.write(typeSection);
        baos.write(importSection);
        baos.write(funcSection);
        baos.write(exportSection);
        baos.write(codeSection);
        return baos.toByteArray();
    }
    public static void main(String[] args) {
        new Compiler();
    }
}
