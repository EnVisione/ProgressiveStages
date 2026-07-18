package com.enviouse.progressivestages.common.rehaul.value;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FormulaCompiler {

    public CompiledFormula compile(String expression) {
        Parser parser = new Parser(expression);
        CompiledFormula.FormulaNode root = parser.expression();
        parser.expect(TokenType.END);
        return new CompiledFormula(expression, parser.dependencies, root);
    }

    private enum TokenType { NUMBER, NAME, PLUS, MINUS, STAR, SLASH, PERCENT, OPEN, CLOSE, COMMA, END }
    private record Token(TokenType type, String text) {}

    private static final class Parser {
        private final List<Token> tokens;
        private final Set<String> dependencies = new LinkedHashSet<>();
        private int index;

        private Parser(String source) {
            tokens = tokenize(source == null ? "" : source);
        }

        private CompiledFormula.FormulaNode expression() {
            CompiledFormula.FormulaNode left = term();
            while (peek(TokenType.PLUS) || peek(TokenType.MINUS)) {
                Token operator = next();
                CompiledFormula.FormulaNode right = term();
                CompiledFormula.FormulaNode previous = left;
                left = values -> operator.type() == TokenType.PLUS
                    ? previous.evaluate(values) + right.evaluate(values)
                    : previous.evaluate(values) - right.evaluate(values);
            }
            return left;
        }

        private CompiledFormula.FormulaNode term() {
            CompiledFormula.FormulaNode left = unary();
            while (peek(TokenType.STAR) || peek(TokenType.SLASH) || peek(TokenType.PERCENT)) {
                Token operator = next();
                CompiledFormula.FormulaNode right = unary();
                CompiledFormula.FormulaNode previous = left;
                left = values -> switch (operator.type()) {
                    case STAR -> previous.evaluate(values) * right.evaluate(values);
                    case SLASH -> previous.evaluate(values) / right.evaluate(values);
                    case PERCENT -> previous.evaluate(values) % right.evaluate(values);
                    default -> throw new IllegalStateException("Invalid formula operator");
                };
            }
            return left;
        }

        private CompiledFormula.FormulaNode unary() {
            if (peek(TokenType.MINUS)) {
                next();
                CompiledFormula.FormulaNode value = unary();
                return values -> -value.evaluate(values);
            }
            if (peek(TokenType.PLUS)) {
                next();
                return unary();
            }
            return primary();
        }

        private CompiledFormula.FormulaNode primary() {
            if (peek(TokenType.NUMBER)) {
                double value = Double.parseDouble(next().text());
                return ignored -> value;
            }
            if (peek(TokenType.OPEN)) {
                next();
                CompiledFormula.FormulaNode value = expression();
                expect(TokenType.CLOSE);
                return value;
            }
            Token name = expect(TokenType.NAME);
            if (peek(TokenType.OPEN)) return function(name.text());
            dependencies.add(name.text());
            return values -> values.getOrDefault(name.text(), 0.0);
        }

        private CompiledFormula.FormulaNode function(String rawName) {
            String name = rawName.toLowerCase(Locale.ROOT);
            expect(TokenType.OPEN);
            List<CompiledFormula.FormulaNode> arguments = new ArrayList<>();
            if (!peek(TokenType.CLOSE)) {
                do {
                    arguments.add(expression());
                    if (!peek(TokenType.COMMA)) break;
                    next();
                } while (true);
            }
            expect(TokenType.CLOSE);
            int expected = name.equals("clamp") ? 3 : name.equals("abs") ? 1 : 2;
            if (arguments.size() != expected) throw new IllegalArgumentException("Formula function has the wrong argument count. " + name);
            return values -> switch (name) {
                case "min" -> Math.min(arguments.get(0).evaluate(values), arguments.get(1).evaluate(values));
                case "max" -> Math.max(arguments.get(0).evaluate(values), arguments.get(1).evaluate(values));
                case "abs" -> Math.abs(arguments.get(0).evaluate(values));
                case "clamp" -> Math.max(arguments.get(1).evaluate(values),
                    Math.min(arguments.get(2).evaluate(values), arguments.get(0).evaluate(values)));
                default -> throw new IllegalArgumentException("Unknown formula function. " + name);
            };
        }

        private boolean peek(TokenType type) { return tokens.get(index).type() == type; }
        private Token next() { return tokens.get(index++); }
        private Token expect(TokenType type) {
            Token token = next();
            if (token.type() != type) throw new IllegalArgumentException("Unexpected formula token. " + token.text());
            return token;
        }
    }

    private static List<Token> tokenize(String source) {
        List<Token> output = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char value = source.charAt(index);
            if (Character.isWhitespace(value)) { index++; continue; }
            TokenType symbol = switch (value) {
                case '+' -> TokenType.PLUS;
                case '-' -> TokenType.MINUS;
                case '*' -> TokenType.STAR;
                case '/' -> TokenType.SLASH;
                case '%' -> TokenType.PERCENT;
                case '(' -> TokenType.OPEN;
                case ')' -> TokenType.CLOSE;
                case ',' -> TokenType.COMMA;
                default -> null;
            };
            if (symbol != null) { output.add(new Token(symbol, String.valueOf(value))); index++; continue; }
            int start = index;
            if (Character.isDigit(value) || value == '.') {
                index++;
                while (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) index++;
                output.add(new Token(TokenType.NUMBER, source.substring(start, index)));
                continue;
            }
            if (Character.isLetter(value) || value == '_') {
                index++;
                while (index < source.length()) {
                    char character = source.charAt(index);
                    if (!Character.isLetterOrDigit(character) && character != '_' && character != ':' && character != '.') break;
                    index++;
                }
                output.add(new Token(TokenType.NAME, source.substring(start, index)));
                continue;
            }
            throw new IllegalArgumentException("Invalid formula character. " + value);
        }
        output.add(new Token(TokenType.END, ""));
        return List.copyOf(output);
    }
}
