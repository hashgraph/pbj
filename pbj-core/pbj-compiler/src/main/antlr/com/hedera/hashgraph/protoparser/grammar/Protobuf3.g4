// SPDX-License-Identifier: Apache-2.0
/**
 * A Protocol Buffers 3 grammar
 *
 * Original source: https://developers.google.com/protocol-buffers/docs/reference/proto3-spec
 * Original source is published under Apache License 2.0.
 *
 * Changes from the source above:
 * - rewrite to antlr.
 * - extract some group to rule.
 * - Allow document comments in a few added places.
 */

grammar Protobuf3;

proto
  : DOC_COMMENT?
    syntax
    ( importStatement
      | packageStatement
      | optionStatement
      | topLevelDef
      | emptyStatement_
      | optionComment
      | DOC_COMMENT
    )*
  ;

docComment: DOC_COMMENT*;

optionComment: OPTION_LINE_COMMENT;

// Syntax

syntax
  : SYNTAX EQ (PROTO3_LIT_SINGLE | PROTO3_LIT_DOUBLE) SEMI
  ;

// Import Statement

importStatement
  : IMPORT ( WEAK | PUBLIC )? strLit SEMI
  ;

// Package

packageStatement
  : PACKAGE fullIdent SEMI
  ;

// Option

optionStatement
  : OPTION optionName EQ constant SEMI
  ;

optionName
  : fullIdent
  | LP fullIdent RP ( DOT fullIdent )?
  ;

// Normal Field

field
  : docComment ( REPEATED )? type_ fieldName EQ fieldNumber ( LB fieldOptions RB )? SEMI
  ;

fieldOptions
  : fieldOption ( COMMA  fieldOption )*
  ;

fieldOption
  : optionName EQ constant
  ;

fieldNumber
  : intLit
  ;

// Oneof and oneof field
// The DOC_COMMENT option in the list below is to support commented-out oneof variants, or just random doc comments inside oneofs.
oneof
  : docComment ONEOF oneofName LC ( optionStatement | oneofField | emptyStatement_ | DOC_COMMENT )* RC
  ;

oneofField
  : docComment type_ fieldName EQ fieldNumber ( LB fieldOptions RB )? SEMI
  ;

// Map field

mapField
  : docComment MAP LT keyType COMMA type_ GT mapName
        EQ fieldNumber ( LB fieldOptions RB )? SEMI
  ;
keyType
  : INT32
  | INT64
  | UINT32
  | UINT64
  | SINT32
  | SINT64
  | FIXED32
  | FIXED64
  | SFIXED32
  | SFIXED64
  | BOOL
  | STRING
  ;

// field types

type_
  : DOUBLE
  | FLOAT
  | INT32
  | INT64
  | UINT32
  | UINT64
  | SINT32
  | SINT64
  | FIXED32
  | FIXED64
  | SFIXED32
  | SFIXED64
  | BOOL
  | STRING
  | BYTES
  | messageType
  | enumType
  ;

// Reserved

reserved
  : DOC_COMMENT? RESERVED ( ranges | reservedFieldNames ) SEMI
  ;

ranges
  : range_ ( COMMA range_ )*
  ;

range_
  : intLit ( TO ( intLit | MAX ) )?
  ;

reservedFieldNames
  : strLit ( COMMA strLit )*
  ;

// Top Level definitions

topLevelDef
  : messageDef
  | enumDef
  | serviceDef
  ;

// enum

enumDef
  : docComment ENUM enumName enumBody
  ;

enumBody
  : LC enumElement* RC
  ;

enumElement
  : optionStatement
  | optionComment
  | enumField
  | emptyStatement_
  ;

enumField
  : docComment ident EQ ( MINUS )? intLit enumValueOptions?SEMI
  ;

enumValueOptions
  : LB enumValueOption ( COMMA  enumValueOption )* RB
  ;

enumValueOption
  : optionName EQ constant
  ;

// message

messageDef
  : docComment ( optionComment )? MESSAGE messageName messageBody
  ;

messageBody
  : LC messageElement* RC
  ;

messageElement
  : field
  | enumDef
  | messageDef
  | optionStatement
  | optionComment
  | oneof
  | mapField
  | reserved
  | emptyStatement_
  ;

// service

serviceDef
  : docComment SERVICE serviceName LC serviceElement* RC
  ;

serviceElement
  : optionStatement
  | optionComment
  | rpc
  | emptyStatement_
  ;

rpc
  : docComment RPC rpcName LP rpcRequestType RP
        RETURNS LP rpcReplyType RP
        (LC ( optionStatement | emptyStatement_ )* RC | SEMI)
  ;

rpcRequestType: rpcType;
rpcReplyType: rpcType;
rpcType: ( STREAM )? messageType;

// lexical

constant
  : fullIdent
  | (MINUS | PLUS )? intLit
  | ( MINUS | PLUS )? floatLit
  | strLit
  | boolLit
  | blockLit
  ;

// not specified in specification but used in tests
blockLit
  : LC ( ident COLON constant )* RC
  ;

emptyStatement_: SEMI;

// Lexical elements

ident: IDENTIFIER | keywords;
fullIdent: ident ( DOT ident )*;
messageName: ident;
enumName: ident;
fieldName: ident;
oneofName: ident;
mapName: ident;
serviceName: ident;
rpcName: ident;
messageType: ( DOT )? ( ident DOT )* messageName;
enumType: ( DOT )? ( ident DOT )* enumName;

intLit: INT_LIT;
strLit: STR_LIT | PROTO3_LIT_SINGLE | PROTO3_LIT_DOUBLE;
boolLit: BOOL_LIT;
floatLit: FLOAT_LIT;

// keywords
SYNTAX: 'syntax';
IMPORT: 'import';
WEAK: 'weak';
PUBLIC: 'public';
PACKAGE: 'package';
OPTION: 'option';
REPEATED: 'repeated';
ONEOF: 'oneof';
MAP: 'map';
INT32: 'int32';
INT64: 'int64';
UINT32: 'uint32';
UINT64: 'uint64';
SINT32: 'sint32';
SINT64: 'sint64';
FIXED32: 'fixed32';
FIXED64: 'fixed64';
SFIXED32: 'sfixed32';
SFIXED64: 'sfixed64';
BOOL: 'bool';
STRING: 'string';
DOUBLE: 'double';
FLOAT: 'float';
BYTES: 'bytes';
RESERVED: 'reserved';
TO: 'to';
MAX: 'max';
ENUM: 'enum';
MESSAGE: 'message';
SERVICE: 'service';
RPC: 'rpc';
STREAM: 'stream';
RETURNS: 'returns';

PROTO3_LIT_SINGLE: '"proto3"';
PROTO3_LIT_DOUBLE: '\'proto3\'';

// symbols

SEMI: ';';
EQ: '=';
LP: '(';
RP: ')';
LB: '[';
RB: ']';
LC: '{';
RC: '}';
LT: '<';
GT: '>';
DOT: '.';
COMMA: ',';
COLON: ':';
PLUS: '+';
MINUS: '-';

STR_LIT: ( '\'' ( CHAR_VALUE )*? '\'' ) |  ( '"' ( CHAR_VALUE )*? '"' );
fragment CHAR_VALUE: HEX_ESCAPE | OCT_ESCAPE | CHAR_ESCAPE | ~[\u0000\n\\];
fragment HEX_ESCAPE: '\\' ( 'x' | 'X' ) HEX_DIGIT HEX_DIGIT;
fragment OCT_ESCAPE: '\\' OCTAL_DIGIT OCTAL_DIGIT OCTAL_DIGIT;
fragment CHAR_ESCAPE: '\\' ( 'a' | 'b' | 'f' | 'n' | 'r' | 't' | 'v' | '\\' | '\'' | '"' );

BOOL_LIT: 'true' | 'false';

FLOAT_LIT : ( DECIMALS DOT DECIMALS? EXPONENT? | DECIMALS EXPONENT | DOT DECIMALS EXPONENT? ) | 'inf' | 'nan';
fragment EXPONENT  : ( 'e' | 'E' ) (PLUS | MINUS)? DECIMALS;
fragment DECIMALS  : DECIMAL_DIGIT+;

INT_LIT     : DECIMAL_LIT | OCTAL_LIT | HEX_LIT;
fragment DECIMAL_LIT : ( [1-9] ) DECIMAL_DIGIT*;
fragment OCTAL_LIT   : '0' OCTAL_DIGIT*;
fragment HEX_LIT     : '0' ( 'x' | 'X' ) HEX_DIGIT+ ;

IDENTIFIER: LETTER ( LETTER | DECIMAL_DIGIT )*;

fragment LETTER: [A-Za-z_];
fragment DECIMAL_DIGIT: [0-9];
fragment OCTAL_DIGIT: [0-7];
fragment HEX_DIGIT: [0-9A-Fa-f];

// comments
WS  :   [ \t\r\n\u000C]+ -> skip;
OPTION_LINE_COMMENT: '//' WS? '<<<' .*? '>>>' ~[\r\n]*;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
DOC_COMMENT: '/**' .*? '*/';
COMMENT: '/*' .*? '*/' -> skip;

keywords
  : SYNTAX
  | IMPORT
  | WEAK
  | PUBLIC
  | PACKAGE
  | OPTION
  | REPEATED
  | ONEOF
  | MAP
  | INT32
  | INT64
  | UINT32
  | UINT64
  | SINT32
  | SINT64
  | FIXED32
  | FIXED64
  | SFIXED32
  | SFIXED64
  | BOOL
  | STRING
  | DOUBLE
  | FLOAT
  | BYTES
  | RESERVED
  | TO
  | MAX
  | ENUM
  | MESSAGE
  | SERVICE
  | RPC
  | STREAM
  | RETURNS
  | BOOL_LIT
  ;
