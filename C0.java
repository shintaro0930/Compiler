import java.io.*;
import java.util.*;
import java.util.function.Function;

import javax.naming.InsufficientResourcesException;
import javax.swing.plaf.multi.MultiOptionPaneUI;
import javax.swing.plaf.synth.SynthPasswordFieldUI;

public class C0 {
    BufferedReader source;
    int line_number;
    char ch = ' ';
    String id_string;
    int literal_value;

    enum token {
        END_PROGRAM,
        IDENTIFIER, LITERAL,
        ELSE, IF, WHILE, BREAK,
        COMMA, SEMICOLON,
        LEFT_BRACE, RIGHT_BRACE, LEFT_PAREN, RIGHT_PAREN,
        EQUAL, OROR, ANDAND, OR, AND,
        EQEQ, NOTEQ, LE/* <= */, LT/* < */, GE/* >= */, GT/* > */,
        PLUS, MINUS, STAR, SLASH, PERCENT
    }

    token sy;

    enum type {
        Variable, Function
    };

    class id_record {
        type id_class; /* 変数か関数かの区別 */
        int address; /* （変数の場合のみ）変数のアドレス */
        int function_id; /* （関数の場合のみ）関数の識別番号 */
        int parameter_count; /* （関数の場合のみ）引数の個数 */

        id_record(type a, int b, int c, int d) { /* コンストラクタ */
            this.id_class = a;
            this.address = b;
            this.function_id = c;
            this.parameter_count = d;
        }
    }

    enum operation { // LCONSTは定数宣言、LOADは変数宣言(変数は任意の数字に置き換える)、STOREは命令したものを保持(これも変数を任意の文字に置き換える)、CALLは関数宣言(これも同じ)
        LCONST, LOAD, STORE, POPUP,
        CALL, JUMP, FJUMP, TJUMP, HALT,
        MULT, DIV, MOD, ADD, SUB, ANDOP, OROP,
        EQOP, NEOP, LEOP, LTOP, GEOP, GTOP
    };

    class code_type {
        operation op_code;
        int operand;
    };

    final int CODE_MAX = 5000;
    int pc = 0;
    code_type code[] = new code_type[CODE_MAX];

    // true or false
    final boolean debug_parse = false;

    void polish(String s) {
        if (debug_parse)
            System.out.print(s + " ");
    }

    // 第３回
    Map<String, id_record> symbol_table;
    int variable_count;

    void init_symbol_table() {
        symbol_table = new TreeMap<String, id_record>();
        variable_count = 0;
        // 四つの標準関数の記号表への登録
        id_record x = new id_record(type.Function, -1, 1, 2);
        symbol_table.put("putd", x);
        id_record y = new id_record(type.Function, -1, 0, 0);
        symbol_table.put("getd", y);
        id_record z = new id_record(type.Function, -1, 2, 0);
        symbol_table.put("newline", z);
        id_record w = new id_record(type.Function, -1, 3, 1);
        symbol_table.put("putchar", w);
    }

    id_record search(String name) {
        if (symbol_table.get(name) == null) {
            id_record new_record = new id_record(type.Variable, variable_count++, -1, -1);
            symbol_table.put(name, new_record);
            return symbol_table.get(name);
        }
        return symbol_table.get(name);
    }

    id_record lookup_variable(String name) {
        id_record f = search(name);
        if (f.id_class == type.Function) { // Functionの時
            error("return type.Function in function: lookup_variable");
        }
        return f;
    }

    id_record lookup_function(String name) {
        id_record f = search(name);
        if (f.id_class == type.Variable) { // Valiableの時
            error("return type.Variable in function: lookup_function");
        }
        return f;
    }

    void next_ch() {
        try {
            ch = (char) source.read();
            if (ch == '\n') {
                line_number++;
            }
        } catch (Exception e) {
            System.out.println("IO error occured");
            System.exit(1);
        }
    }

    void get_token() {
        while (ch == ' ' || ch == '\n' || ch == '\t') {
            next_ch();
        }
        if (ch == 65535) {
            sy = token.END_PROGRAM;
            return;
        }
        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_') {
            /* 英字 or 下線 */
            id_string = "";
            while ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                id_string += ch;
                next_ch();
            }
            if (id_string.equals("else")) {
                sy = token.ELSE;
                return;
            }
            if (id_string.equals("if")) {
                sy = token.IF;
                return;
            }
            if (id_string.equals("while")) {
                sy = token.WHILE;
                return;
            } else {
                sy = token.IDENTIFIER;
                return;
            }
        } else if (ch >= '0' && ch <= '9') {
            /* 数字 */
            int v = 0;
            int c = 0;
            while (ch >= '0' && ch <= '9') {
                int a = ch - '0';
                c = v;
                v = v * 10 + a;
                next_ch();
                if (c > v) {
                    while (ch >= '0' && ch <= '9') {
                        next_ch();
                    }
                    literal_value = 0;
                    sy = token.LITERAL;
                    error("OverFlow");
                    return;
                }

                if ((c >= 214748365) || ((c == 214748364) && ch > '7')) {
                    while (ch >= '0' && ch <= '9') {
                        next_ch();
                    }
                    literal_value = 0;
                    sy = token.LITERAL;
                    error("OverFlow");
                    return;
                }
            }
            literal_value = v;
            sy = token.LITERAL;
            return;
        } else {
            /* 記号 */

            if (ch == '(') {
                next_ch();
                sy = token.LEFT_PAREN;
                return;
            } else if (ch == ')') {
                next_ch();
                sy = token.RIGHT_PAREN;
                return;
            } else if (ch == '{') {
                next_ch();
                sy = token.LEFT_BRACE;
                return;
            } else if (ch == '}') {
                next_ch();
                sy = token.RIGHT_BRACE;
                return;
            } else if (ch == ',') { // ,
                next_ch();
                sy = token.COMMA;
                return;
            } else if (ch == ';') { // ;
                next_ch();
                sy = token.SEMICOLON;
                return;
            } else if (ch == '|') {
                next_ch();
                if (ch == '|') {
                    next_ch();
                    sy = token.OROR;
                    return;
                }
                sy = token.OR;
                return;
            } else if (ch == '!') {
                next_ch();
                if (ch == '=') {
                    next_ch();
                    sy = token.NOTEQ;
                    return;
                } else {
                    // ! だけの時
                    error("Syntax Error : !");
                    get_token();
                }
                return;
            } else if (ch == '<') {
                next_ch();
                if (ch == '=') {
                    next_ch();
                    sy = token.LE;
                    return;
                }
                sy = token.LT;
                return;
            } else if (ch == '>') {
                next_ch();
                if (ch == '=') {
                    next_ch();
                    sy = token.GE;
                    return;
                }
                sy = token.GT;
                return;
            } else if (ch == '+') {
                next_ch();
                sy = token.PLUS;
                return;
            } else if (ch == '-') {
                next_ch();
                sy = token.MINUS;
                return;
            } else if (ch == '*') {
                next_ch();
                sy = token.STAR;
                return;
            } else if (ch == '/') {
                next_ch();
                if (ch == '*') {
                    while (true) {
                        next_ch();
                        while (ch == '*') {
                            next_ch();
                            if (ch == '/') {
                                next_ch();
                                get_token();
                                return;
                            }
                        }
                        if (ch == 65535) {
                            error("comment not terminated");
                            sy = token.END_PROGRAM;
                            return;
                        }
                    }
                }
                sy = token.SLASH;
                return;
            } else if (ch == '%') {
                next_ch();
                sy = token.PERCENT;
                return;
            } else if (ch == '=') { // =
                next_ch();
                if (ch == '=') { // ==
                    next_ch();
                    sy = token.EQEQ;
                    return;
                }
                sy = token.EQUAL;
                return;
            } else if (ch == '&') { // &
                next_ch();
                if (ch == '&') { // &&
                    next_ch();
                    sy = token.ANDAND;
                    return;
                }
                sy = token.AND;
                return;
            } else {
                next_ch();
                get_token();
                error("Unexpected Error");
                return;
            }
        }
    }// get_token()

    // 第２回
    void statement() {
        if (sy == token.SEMICOLON) {
            get_token();
            polish("empty statement\n");
        } else if (sy == token.IF) {
            get_token();
            polish("if statement: ");
            if (sy == token.LEFT_PAREN) {
                get_token();
                expression();
                if (sy == token.RIGHT_PAREN) {
                    get_token();
                    int if_save = pc;
                    emit(operation.FJUMP, 0);
                    polish("\n");
                    statement();
                    if (sy == token.ELSE) {
                        get_token();
                        int else_save = pc;
                        code[if_save].operand = else_save + 1;
                        emit(operation.JUMP, 0);
                        polish("else part\n");
                        statement();
                        code[else_save].operand = pc;
                    } else {
                        code[if_save].operand = pc;
                    }
                    polish("end if statement\n");
                } else {
                    error("expected right paren");
                }
            } else {
                error("expected right paren");
            }
        } else if (sy == token.WHILE) {
            int while_start = pc;
            get_token();
            polish("while statement: ");
            if (sy == token.LEFT_PAREN) {
                get_token();
                expression();
                int pc_save = pc;
                emit(operation.FJUMP, 0); // 適当に0
                if (sy == token.RIGHT_PAREN) {
                    polish("\n");
                    get_token();
                    statement();
                    emit(operation.JUMP, while_start);
                    polish("end while statement\n");
                    code[pc_save].operand = pc;
                } else {
                    error("expected right paren");
                }
            } else {
                error("expected right paren");
            }
        } else if (sy == token.LEFT_BRACE) {
            get_token();
            if (sy == token.RIGHT_BRACE) {
                get_token();
            } else {
                while (sy != token.RIGHT_BRACE) {
                    statement();
                    if (sy == token.END_PROGRAM) {
                        error("EOF");
                        return;
                    }
                }
                get_token();
            }
        } else { // 式
            expression();
            if (sy == token.SEMICOLON) {
                get_token();
                polish("\n");
                emit(operation.POPUP, 0);
            } else {
                error("semicolon expected");
            }
        }
    }

    int re_look_va, re_look_fu;

    void expression() {
        logical_or_expression();
        if (sy == token.EQUAL) {
            if (code[pc - 1].op_code != operation.LOAD)
                error("assignment to non-variable");
            int left_ope = code[pc - 1].operand;
            pc--;
            get_token();
            expression();
            polish("=");
            emit(operation.STORE, left_ope);
        }
    }

    void logical_or_expression() {
        logical_and_expression();
        while (sy == token.OROR) {
            int pc_save = pc;
            emit(operation.TJUMP, 0);
            get_token();
            logical_and_expression();
            polish("||");
            emit(operation.TJUMP, pc + 3);
            emit(operation.LCONST, 0); // false
            emit(operation.JUMP, pc + 2);
            emit(operation.LCONST, 1); // true
            code[pc_save].operand = pc - 1;
        }
    }

    void logical_and_expression() {
        bit_or_expression();
        while (sy == token.ANDAND) {
            int pc_save = pc;
            emit(operation.FJUMP, 0);
            get_token();
            bit_or_expression();
            polish("&&");
            emit(operation.FJUMP, pc + 3);
            emit(operation.LCONST, 1); // true
            emit(operation.JUMP, pc + 2);
            emit(operation.LCONST, 0); // false
            code[pc_save].operand = pc - 1;
        }
    }

    void bit_or_expression() {
        bit_and_expression();
        while (sy == token.OR) {
            get_token();
            bit_and_expression();
            polish("|");
            emit(operation.OROP, 0);
        }
    }

    void bit_and_expression() {
        equality_expression();
        while (sy == token.AND) {
            get_token();
            equality_expression();
            polish("&");
            emit(operation.ANDOP, 0);
        }
    }

    void equality_expression() {
        relational_expression();
        while (true) {
            if (sy == token.EQEQ) {
                get_token();
                relational_expression();
                polish("==");
                emit(operation.EQOP, 0);
            } else if (sy == token.NOTEQ) {
                get_token();
                relational_expression();
                polish("!=");
                emit(operation.NEOP, 0);
            } else {
                break;
            }
        }
    }

    void relational_expression() {
        additive_expression();
        while (true) {
            if (sy == token.LT) {
                get_token();
                additive_expression();
                polish("<");
                emit(operation.LTOP, 0);
            } else if (sy == token.GT) { // >
                get_token();
                additive_expression();
                polish(">");
                emit(operation.GTOP, 0);
            } else if (sy == token.LE) {
                get_token();
                additive_expression();
                polish("<=");
                emit(operation.LEOP, 0);
            } else if (sy == token.GE) {
                get_token();
                additive_expression();
                polish(">=");
                emit(operation.GEOP, 0);
            } else {
                break;
            }
        }
    }

    void additive_expression() {
        multiplicative_expression();
        while (true) {
            if (sy == token.PLUS) {
                get_token();
                multiplicative_expression();
                polish("+");
                emit(operation.ADD, 0);
            } else if (sy == token.MINUS) {
                get_token();
                multiplicative_expression();
                polish("-");
                emit(operation.SUB, 0);
            } else {
                break;
            }
        }
    }

    void multiplicative_expression() {
        unary_expression();
        while (true) {
            if (sy == token.STAR) {
                get_token();
                unary_expression();
                polish("*");
                emit(operation.MULT, 0);
            } else if (sy == token.SLASH) {
                get_token();
                unary_expression();
                polish("/");
                emit(operation.DIV, 0);
            } else if (sy == token.PERCENT) {
                get_token();
                unary_expression();
                polish("%");
                emit(operation.MOD, 0);
            } else {
                break;
            }
        }
    }

    void unary_expression() {
        if (sy == token.MINUS) {
            emit(operation.LCONST, 0);
            get_token();
            unary_expression();
            polish("u-");
            emit(operation.SUB, 0);
        } else {
            primary_expression();
        }
    }

    void primary_expression() {
        if (sy == token.LITERAL) {
            emit(operation.LCONST, literal_value);
            polish(literal_value + "");
            get_token();
        } else if (sy == token.IDENTIFIER) { // 識別子の時
            polish(id_string);
            get_token();
            if (sy == token.LEFT_PAREN) { // 関数呼び出し
                id_record f = lookup_function(id_string);
                int cnt = 0;
                get_token();
                if (sy == token.RIGHT_PAREN) { // 引数が0の関数呼び出し
                    get_token();
                    polish("call-" + cnt);
                    if (f.parameter_count != cnt) {
                        error("diffrent parameter lookup_function and cnt");
                    }
                    re_look_fu = f.function_id;
                    emit(operation.CALL, re_look_fu);
                } else { // 引数が1以上の時
                    expression();
                    cnt++;
                    while (sy == token.COMMA) {
                        cnt++;
                        get_token();
                        expression();
                    }
                    if (sy == token.RIGHT_PAREN) {
                        get_token();
                        polish("call-" + cnt);
                        if (f.parameter_count != cnt) {
                            error("diffrent parameter lookup_function and cnt");
                        }
                        re_look_fu = f.function_id;
                        emit(operation.CALL, re_look_fu);
                    } else {
                        error("expected right paren");
                    }
                }
            } else {
                // 変数参照の時
                lookup_variable(id_string);
                re_look_va = lookup_variable(id_string).address;
                emit(operation.LOAD, re_look_va);
            }
        } else if (sy == token.LEFT_PAREN) { // ( 式 )
            get_token();
            expression();
            if (sy == token.RIGHT_PAREN)
                get_token();
            else
                error("right parenthesis expected");
        } else {
            error("unrecognized element in primary_expression");
            get_token();
        }
    }

    void run_error(String s) {
        System.out.println(s);
        System.exit(1);
    }

    final int Stack_Size = 100;
    int memory_size;
    int memory[];
    int sp, ic;

    void interpret(boolean trace) {
        memory_size = variable_count + Stack_Size;
        memory = new int[memory_size];

        Scanner sc = new Scanner(System.in);
        ic = 0;
        sp = variable_count;
        for (;;) {
            operation instruction = code[ic].op_code; // 命令コード
            int argument = code[ic].operand; // オペランド
            if (trace) {
                System.out.print("ic=" + String.format("%4d", ic) +
                        ", sp=" + String.format("%5d", sp) +
                        ", code=(" +
                        String.format("%-6s", instruction) +
                        String.format("%6d", argument) + ")");
                if (sp > variable_count) {
                    int val = pop();
                    push(val);
                    System.out.print(", top=" + String.format("%10d", val));
                }
                System.out.println();
            }
            ic++;
            switch (instruction) {
                case LCONST:
                    push(argument);
                    continue;
                case LOAD:
                    if (argument < 0 || argument >= variable_count)
                        error("Adress Error");
                    push(memory[argument]);
                    continue;
                case STORE:
                    int save = pop();
                    memory[argument] = save;
                    if (argument < 0 || argument >= variable_count)
                        error("Adress Error");
                    push(memory[argument]);
                    continue;
                case POPUP:
                    pop();
                    continue;
                case CALL:
                    if (argument == 0) { // getd
                        System.out.print("getd: ");
                        push(sc.nextInt());
                    } else if (argument == 1) { // putd
                        int width = pop();
                        int val = pop();
                        String s = String.format("%d", val);
                        int d = width - s.length();
                        while (d > 0) {
                            System.out.print(" ");
                            d--;
                        }
                        System.out.print(s);
                        push(val);
                    } else if (argument == 2) { // newline
                        System.out.println();
                        push(0);
                    } else if (argument == 3) { // putchar
                        int val = pop();
                        char c = (char) val;
                        System.out.print(c);
                        push(val);
                    } else {
                        run_error("Unregistered Function");
                    }
                    continue;
                case JUMP:
                    ic = argument;
                    continue;
                case FJUMP:
                    int fjump_save = pop();
                    if (fjump_save == 0) {
                        ic = argument;
                    }
                    continue;
                case TJUMP:
                    int tjump_save = pop();
                    if (tjump_save != 0) {
                        ic = argument;
                    }
                    continue;
                case HALT:
                    if (sp != variable_count) {
                        error("Error");
                    }
                    return;
                case MULT:
                    push(pop() * pop());
                    continue;
                case ADD:
                    push(pop() + pop());
                    continue;
                case SUB:
                    int a1 = pop(); // 後ろ
                    int b1 = pop(); // 前
                    push(b1 - a1);
                    continue;
                case ANDOP:
                    int a10 = pop();
                    int b10 = pop();
                    int result10 = a10 & b10;
                    push(result10);
                    continue;
                case OROP:
                    // push(OROP);
                    int a11 = pop();
                    int b11 = pop();
                    int result11 = a11 | b11;
                    push(result11);
                    continue;
                case DIV:
                    int a2 = pop();
                    int b2 = pop();
                    if (a2 == 0)
                        run_error("Error");
                    push(b2 / a2);
                    continue;
                case MOD: // DIVとMODも同じ
                    int a3 = pop();
                    int b3 = pop();
                    if (a3 == 0)
                        run_error("Error");
                    push(b3 % a3);
                    continue;
                case EQOP:
                    int a4 = pop();
                    int b4 = pop();
                    if (a4 == b4)
                        push(1);
                    else
                        push(0);
                    continue;
                case NEOP:
                    int a5 = pop();
                    int b5 = pop();
                    if (a5 != b5)
                        push(1);
                    else
                        push(0);
                    continue;
                case LEOP:
                    int a6 = pop();
                    int b6 = pop();
                    // System.out.println(b6 + "<=" + a6);
                    if (b6 <= a6)
                        push(1); // 1
                    else
                        push(0); // 0
                    continue;
                case LTOP:
                    int a7 = pop();
                    int b7 = pop();
                    // System.out.println(b7 + "<" + a7);
                    if (b7 < a7)
                        push(1);
                    else
                        push(0);
                    continue;
                case GEOP:
                    int a8 = pop();
                    int b8 = pop();
                    if (b8 >= a8)
                        push(1); // 1
                    else
                        push(0); // 0
                    continue;
                case GTOP:
                    int a9 = pop();
                    int b9 = pop();
                    if (b9 > a9)
                        push(1); // 1
                    else
                        push(0); // 0
                    continue;
                default:
                    run_error("system error: undefined op code");
            }
        }
    }

    void push(int x) {
        if (sp >= memory_size)
            run_error("stack overflow");
        memory[sp] = x;
        sp++;
    }

    int pop() {
        if (sp <= variable_count)
            run_error("system error: stack underflow");
        sp--;
        return (memory[sp]);
    }

    public static void main(String[] args) throws Exception {
        C0 C0_instance = new C0();
        C0_instance.driver(args);
    }

    // 第３回
    void print_code() {
        for (int i = 0; i < pc; i++)
            System.out.println(String.format("%5d", i) + ": " +
                    String.format("%-6s", code[i].op_code) +
                    String.format("%6d", code[i].operand));
    }

    void emit(operation op, int param) {
        if (pc >= CODE_MAX) {
            error("pc >= CODE_MAX in function: emit");
            System.exit(1);
        }
        code[pc] = new code_type();
        code[pc].op_code = op;
        code[pc].operand = param;
        pc++;
    }

    int error_count = 0;

    void error(String s) {
        System.out.println(String.format("%4d", line_number) + ": " + s);
        error_count++;
    }

    void driver(String[] args) throws Exception {
        if (args.length == 1) {
            source = new BufferedReader(new FileReader(new File(args[0])));
        } else {
            source = new BufferedReader(new InputStreamReader(System.in));
            if (args.length != 0) {
                error("multiple source file is not supported");
            }
        }
        line_number = 1;
        ch = ' ';
        init_symbol_table();
        get_token();
        statement();
        emit(operation.HALT, 0);
        // print_code();
        if (sy != token.END_PROGRAM)
            error("extra text at the end of the program");
        interpret(false);

    }
}
