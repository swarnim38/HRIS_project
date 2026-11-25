package io;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Csv {
    public record Row(int line, List<String> cells, String error) {
        public Row { cells = List.copyOf(cells); }
    }

    private enum State { START, UNQUOTED, QUOTED, CLOSED, INVALID }

    private Csv() {}

    public static List<Row> parse(String input) {
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.START;
        int line = 1;
        int start = 1;
        int offset = input.startsWith("\uFEFF") ? 1 : 0;
        for (int i = offset; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\r' || ch == '\n') {
                boolean crlf = ch == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n';
                if (state == State.QUOTED) {
                    field.append(crlf ? "\r\n" : String.valueOf(ch));
                } else {
                    finish(rows, cells, field, state, start);
                    state = State.START;
                    start = line + 1;
                }
                if (crlf) i++;
                line++;
            } else if (state == State.QUOTED) {
                if (ch == '"') state = State.CLOSED;
                else field.append(ch);
            } else if (state != State.INVALID) {
                if (ch == ',') {
                    cells.add(field.toString());
                    field.setLength(0);
                    state = State.START;
                } else if (ch == '"' && state == State.CLOSED) {
                    field.append(ch);
                    state = State.QUOTED;
                } else if (ch == '"' && state == State.START) {
                    state = State.QUOTED;
                } else if (ch == '"' || state == State.CLOSED) {
                    state = State.INVALID;
                } else {
                    field.append(ch);
                    state = State.UNQUOTED;
                }
            }
        }
        if (state != State.START || !cells.isEmpty()) finish(rows, cells, field, state, start);
        return List.copyOf(rows);
    }

    private static void finish(List<Row> rows, List<String> cells, StringBuilder field, State state, int start) {
        if (state == State.INVALID || state == State.QUOTED) {
            rows.add(new Row(start, List.of(), state == State.INVALID ? "Malformed CSV quoting" : "Unclosed CSV quote"));
        } else if (!cells.isEmpty() || state != State.START) {
            cells.add(field.toString());
            if (cells.size() > 1 || !cells.get(0).isBlank()) rows.add(new Row(start, cells, null));
        }
        cells.clear();
        field.setLength(0);
    }

    public static String encode(List<String> cells) {
        return cells.stream().map(Csv::escape).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
