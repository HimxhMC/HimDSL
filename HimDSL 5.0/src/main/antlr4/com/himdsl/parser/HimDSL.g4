grammar HimDSL;

program
    : (structDef | declaration | functionDef)* EOF
    ;

structDef
    : 'struct' IDENTIFIER '{' structMember* '}' ';'
    ;

structMember
    : type fieldName ';'                                        # StructFieldMember
    | STATIC? returnType IDENTIFIER '(' paramList? ')' block    # StructMethodMember
    ;

fieldName
    : IDENTIFIER
    | PLAYER | WORLD | SERVER | BOSS | HAVEVAR
    | INT | DOUBLE | BOOL | STRING_TYPE | VOID
    | IF | ELSE | FOR | WHILE | DO | RETURN
    | TRUE | FALSE | NULL | VAR
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
    : INT | DOUBLE | BOOL | STRING_TYPE | VOID | IDENTIFIER
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

arrayDecl
    : (type | VAR) IDENTIFIER '[' expression? ']' ('=' arrayInitializer)? ';'
    ;

arrayInitializer
    : '{' expression (',' expression)* '}'
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

block
    : '{' statement* '}'
    ;

lvalue
    : IDENTIFIER                         # IdentifierLvalue
    | lvalue '[' expression ']'          # ArrayLvalue
    | lvalue '.' IDENTIFIER              # FieldLvalue  
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
    : ('!' | '-' | '~') unary
    | postfix
    ;

postfix
    : primary ( '[' expression ']'
              | '(' argumentList? ')'
              | '.' IDENTIFIER
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

// ---------- 词法规则（关键字优先） ----------
VAR : 'var';
PLAYER : 'player';
WORLD : 'world';
SERVER : 'server';
BOSS : 'boss';
HAVEVAR : 'haveVar';
INT : 'int';
DOUBLE : 'double';
BOOL : 'bool';
STRING_TYPE : 'string';
VOID : 'void';
IF : 'if';
ELSE : 'else';
FOR : 'for';
WHILE : 'while';
DO : 'do';
RETURN : 'return';
TRUE : 'true';
FALSE : 'false';
NULL : 'null';
STATIC : 'static';
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