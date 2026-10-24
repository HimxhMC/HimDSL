grammar HimDSL;

program
    : (importStatement | structDef | enumDef | declaration | functionDef)* EOF
    ;

importStatement
    : IMPORT importPath ';'
    ;

importPath
    : importSegment ('.' importSegment)* ('.' '*')?
    ;

// 允许包名段出现与 Bukkit 相关的关键字（如 com.player.Xxx / org.world.Yyy）
importSegment
    : IDENTIFIER
    | PLAYER | WORLD | SERVER | BOSS | HAVEVAR | VAR
    ;

enumDef
    : ENUM IDENTIFIER '{' enumMember (',' enumMember)* ','? '}' ';'
    ;

enumMember
    : IDENTIFIER
    ;

structDef
    : 'struct' IDENTIFIER '{' structMember* '}' ';'
    ;

structMember
    : type fieldName ';'                                        # StructFieldMember
    | STATIC? returnType IDENTIFIER '(' paramList? ')' block    # StructMethodMember
    ;

memberName
    : IDENTIFIER
    | PLAYER | WORLD | SERVER | BOSS | HAVEVAR | VAR
    | INT | DOUBLE | BOOL | STRING_TYPE | CHAR_TYPE | VOID
    | IF | ELSE | FOR | WHILE | DO | RETURN
    | TRUE | FALSE | NULL
    | IMPORT | ENUM
    | TRY | CATCH | FINALLY | SWITCH | CASE | DEFAULT
    | BREAK | CONTINUE | STATIC | NEW
    ;

fieldName
    : memberName
    ;

declaration
    : variableDecl
    | playerDecl
    | arrayDecl
    ;

variableDecl
    : (type | VAR) IDENTIFIER ('=' expression)? ';'
    ;

type
    : INT | DOUBLE | BOOL | STRING_TYPE | CHAR_TYPE | VOID | IDENTIFIER
    ;

playerDecl
    : PLAYER IDENTIFIER '=' selector ';'
    ;

selector
    : '@a' | '@s' | '@r'
    | '@p' ('[' selectorArg (',' selectorArg)* ']')?
    | '@e' ('[' selectorArg (',' selectorArg)* ']')?
    ;

selectorArg
    : IDENTIFIER '=' value
    ;

value
    : NUMBER
    | STRING
    | range
    ;

range
    : NUMBER? '..' NUMBER?
    ;

// ★ 多维数组声明
arrayDecl
    : (type | VAR) IDENTIFIER ('[' expression? ']')+ ('=' arrayInitializer)? ';'
    ;

// ★ 支持嵌套初始化器
arrayInitializer
    : '{' (arrayElement (',' arrayElement)* ','?)? '}'
    ;

arrayElement
    : expression          # ArrayElementExpr
    | arrayInitializer    # ArrayElementInit
    ;

functionDef
    : annotation* returnType IDENTIFIER '(' paramList? ')' ('sche' '=' timeSpec)? block
    ;

annotation
    : '@' IDENTIFIER
    ;

returnType
    : type
    | VOID
    ;

paramList
    : param (',' param)*
    ;

param
    : type IDENTIFIER                    #NormalParam
    | IDENTIFIER '(' eventType ')'       #EventParam
    ;

eventType
    : IDENTIFIER
    | STRING
    ;

timeSpec
    : NUMBER ( 's' | 't' | 'ms' )
    | expression
    ;

statement
    : variableDecl
    | playerDecl
    | arrayDecl
    | enumDef
    | tryStatement
    | switchStatement
    | breakStatement
    | continueStatement
    | assignment
    | ifStatement
    | forStatement
    | whileStatement
    | doWhileStatement
    | functionCall ';'
    | returnStatement
    | expression ';'?
    | ';'
    | block
    ;

tryStatement
    : TRY block catchClause* finallyClause?
    ;

catchClause
    : CATCH '(' catchParams ')' block
    ;

catchParams
    : catchType IDENTIFIER    # TypedCatch
    | IDENTIFIER              # AnyCatch
    ;

catchType
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

finallyClause
    : FINALLY block
    ;

switchStatement
    : SWITCH '(' expression ')' '{' switchCase* '}'
    ;

switchCase
    : CASE expression ':' statement*
    | DEFAULT ':' statement*
    ;

breakStatement    : BREAK ';' ;
continueStatement : CONTINUE ';' ;

block
    : '{' statement* '}'
    ;

lvalue
    : IDENTIFIER                         # IdentifierLvalue
    | lvalue '[' expression ']'          # ArrayLvalue
    | lvalue '.' memberName              # FieldLvalue
    ;

functionCall
    : IDENTIFIER '(' argumentList? ')'
    ;

assignment
    : lvalue assignmentOp expression ';'
    ;

assignNoSemi
    : lvalue assignmentOp expression
    ;

varDeclNoSemi
    : (type | VAR) IDENTIFIER ('=' expression)?
    ;

assignmentOp
    : '=' | '+=' | '-=' | '*=' | '/=' | '%='
    ;

ifStatement
    : IF '(' expression ')' statement (ELSE statement)?
    ;

forStatement
    : FOR '(' forInit? ';' forCondition? ';' forUpdate? ')' statement
    ;

forInit
    : varDeclNoSemi
    | assignNoSemi
    ;

forCondition
    : expression
    ;

forUpdate
    : assignNoSemi
    | expression
    ;
    
whileStatement
    : WHILE '(' expression ')' statement
    ;

doWhileStatement
    : DO statement WHILE '(' expression ')' ';'
    ;

returnStatement
    : RETURN expression? ';'
    ;

expression
    : logicalOr
    ;

logicalOr
    : logicalAnd ( '||' logicalAnd )*
    ;

logicalAnd
    : equality ( '&&' equality )*
    ;

equality
    : relational ( ('==' | '!=') relational )*
    ;

relational
    : additive ( ('<' | '>' | '<=' | '>=') additive )*
    ;

additive
    : multiplicative ( ('+' | '-') multiplicative )*
    ;

multiplicative
    : unary ( ('*' | '/' | '%') unary )*
    ;

unary
    : ('!' | '-' | '~') unary     # UnaryOp
    | INCREMENT lvalue            # PreIncrement
    | DECREMENT lvalue            # PreDecrement
    | postfix                     # PostfixUnary
    ;

postfix
    : primary ( '[' expression ']'
              | '(' argumentList? ')'
              | '.' memberName
              | INCREMENT
              | DECREMENT
              )*
    ;

primary
    : NUMBER
    | STRING
    | CHAR
    | TRUE
    | FALSE
    | NULL
    | IDENTIFIER
    | placeholder
    | '(' expression ')'
    | arrayInitializer
    | selector
    | newExpr
    | lambdaExpr
    ;

lambdaExpr
    : IDENTIFIER '->' lambdaBody                              # SingleParamLambda
    | '(' lambdaParamList? ')' '->' lambdaBody                # ParenLambda
    ;

lambdaParamList
    : lambdaParam (',' lambdaParam)*
    ;

lambdaParam
    : type? IDENTIFIER
    ;

lambdaBody
    : block                                                   # BlockLambdaBody
    | expression                                              # ExprLambdaBody
    ;

newExpr
    : NEW classRef '(' argumentList? ')'
    ;

classRef
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

argumentList
    : expression (',' expression)*
    ;

placeholder
    : '$' placeholderType '(' argumentList? ')' '$'
    ;

placeholderType
    : PLAYER
    | WORLD
    | SERVER
    | BOSS
    | HAVEVAR
    ;

// ---------- 词法规则 ----------
VAR : 'var';
PLAYER : [pP]'layer';
WORLD : 'world';
SERVER : 'server';
BOSS : 'boss';
HAVEVAR : 'haveVar';
IMPORT : 'import';
ENUM   : 'enum';
INT : 'int';
DOUBLE : 'double';
BOOL : 'bool';
STRING_TYPE : 'string';
CHAR_TYPE : 'char';
VOID : 'void';
IF : 'if';
ELSE : 'else';
FOR : 'for';
WHILE : 'while';
DO : 'do';
RETURN : 'return';
TRY : 'try';
CATCH : 'catch';
FINALLY : 'finally';
SWITCH : 'switch';
CASE : 'case';
DEFAULT : 'default';
BREAK : 'break';
CONTINUE : 'continue';
TRUE : 'true';
FALSE : 'false';
NULL : 'null';
STATIC : 'static';
INCREMENT : '++';
DECREMENT : '--';
NEW : 'new';
CHAR : '\'' ( '\\' . | ~['\\] ) '\'' ;
NUMBER
    : [0-9]+ ( '.' [0-9]+ )?
    ;

STRING : '"' ( '\\' . | ~["\\] )* '"' ;

IDENTIFIER
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

COMMENT
    : '//' ~[\r\n]* -> skip
    ;

MULTILINE_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;