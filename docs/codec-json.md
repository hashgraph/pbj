# PBJ JSON Codec

This document describes how PBJ's generated JSON codecs work — the parsing approach, generated codec anatomy, and JSON mapping rules.

For the shared codec interfaces and IO abstractions, see [codecs.md](codecs.md). For the standard proto3 JSON mapping rules, see [protobuf-and-schemas.md](protobuf-and-schemas.md#json-mapping).

## Overview

PBJ generates a `JsonCodec<T>` for every protobuf message. JSON codecs use a two-phase approach: raw JSON bytes are first parsed into an ANTLR parse tree, then the tree is walked to extract field values. This is simpler than streaming JSON parsing but means the entire JSON payload must fit in memory (bounded by `maxSize`).

Each model class exposes a singleton JSON codec:

```java
public static final JsonCodec<HelloRequest> JSON = new HelloRequestJsonCodec();

// Usage
HelloRequest msg = HelloRequest.JSON.parse(jsonInput);
String json = HelloRequest.JSON.toJSON(msg);
```

## Generated JSON Codec — Anatomy

### Parse Method

JSON parsing has two phases:

1. **ANTLR parse** — Raw JSON bytes are parsed into an AST (`JSONParser.ObjContext`) via `JsonTools.parseJson()`
2. **Tree walk** — The generated codec iterates over key-value pairs and dispatches on field names

```java
for (JSONParser.PairContext kvPair : root.pair()) {
    switch (kvPair.STRING().getText()) {
        case "name" -> temp_name = unescape(checkSize("name", kvPair.value().STRING().getText(), maxSize));
        case "accountId" -> { /* parse nested message */ }
        default -> {
            if (strictMode) throw new UnknownFieldException(kvPair.STRING().getText());
        }
    }
}
```

Field names in JSON use **camelCase** (converted from proto's snake_case), following the standard protobuf JSON mapping: `account_id` becomes `"accountId"`.

### Write Method (toJSON)

The `toJSON()` method builds a JSON string with optional pretty-printing:

```java
public String toJSON(HelloRequest data, String indent, boolean inline) {
    StringBuilder sb = new StringBuilder();
    sb.append(inline ? "{\n" : indent + "{\n");
    final String childIndent = indent + INDENT;
    final List<String> fieldLines = new ArrayList<>();

    // Only include non-default fields
    if (data.name() != null && !data.name().isEmpty())
        fieldLines.add(field("name", data.name()));

    if (!fieldLines.isEmpty()) {
        sb.append(childIndent);
        sb.append(String.join(",\n" + childIndent, fieldLines));
        sb.append("\n");
    }
    sb.append(indent + "}");
    return sb.toString();
}
```

`JsonTools.field()` overloads handle quoting, escaping, and formatting for each type.

## JSON Mapping Rules

PBJ implements the standard [proto3 JSON mapping](https://protobuf.dev/programming-guides/proto3/#json):

| Proto concept | JSON representation |
|---------------|---------------------|
| Field names | `snake_case` converted to `camelCase` (e.g., `account_id` → `"accountId"`) |
| Default-valued fields | Omitted from output |
| Enums | Serialized as string name |
| `bytes` fields | Base64-encoded strings |
| 64-bit integers | Serialized as strings to avoid JavaScript precision loss |
| Nested messages | JSON objects |
| Repeated fields | JSON arrays |
| Map fields | JSON objects with string keys |

Both strict and non-strict modes are supported: strict mode throws on unrecognized JSON fields, non-strict mode silently ignores them.

## Performance Characteristics

JSON codecs provide default (non-optimized) implementations for `measure()`, `measureRecord()`, and `fastEquals()` since JSON is not considered performance-critical. The ANTLR-based parsing approach builds a full parse tree before walking it, which trades memory for simplicity. This is acceptable for PBJ's target use case where messages are bounded by `maxSize`.
