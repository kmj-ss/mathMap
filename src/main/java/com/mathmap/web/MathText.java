package com.mathmap.web;

import java.util.Map;

/**
 * 문제 글 안의 $...$ 수식(LaTeX)을 엑셀에서 읽기 쉬운 글자로 바꾼다.
 * 예: $\dfrac{3}{4}$ → 3/4, $x^{2}$ → x², $\sqrt{2}$ → √2, $a_{1}$ → a₁
 * 엑셀 칸은 수식을 그릴 수 없어서, 자주 쓰는 표현만 글자로 옮긴다.
 */
final class MathText {

    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("times", "×"), Map.entry("div", "÷"), Map.entry("pm", "±"), Map.entry("mp", "∓"),
            Map.entry("cdot", "·"), Map.entry("le", "≤"), Map.entry("leq", "≤"), Map.entry("ge", "≥"),
            Map.entry("geq", "≥"), Map.entry("ne", "≠"), Map.entry("neq", "≠"), Map.entry("approx", "≈"),
            Map.entry("pi", "π"), Map.entry("circ", "°"), Map.entry("degree", "°"), Map.entry("infty", "∞"),
            Map.entry("alpha", "α"), Map.entry("beta", "β"), Map.entry("gamma", "γ"), Map.entry("theta", "θ"),
            Map.entry("angle", "∠"), Map.entry("triangle", "△"), Map.entry("perp", "⊥"), Map.entry("parallel", "∥"),
            Map.entry("therefore", "∴"), Map.entry("because", "∵"), Map.entry("rightarrow", "→"),
            Map.entry("to", "→"), Map.entry("leftarrow", "←"), Map.entry("cdots", "⋯"), Map.entry("ldots", "…"),
            Map.entry("dots", "…"), Map.entry("%", "%"), Map.entry("$", "$"), Map.entry("{", "{"), Map.entry("}", "}"),
            Map.entry(",", " "), Map.entry(";", " "), Map.entry("quad", " "), Map.entry("qquad", "  "),
            Map.entry(" ", " "), Map.entry("left", ""), Map.entry("right", ""), Map.entry("displaystyle", ""));

    private static final String SUP_FROM = "0123456789+-=()n";
    private static final String SUP_TO = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿ";
    private static final String SUB_FROM = "0123456789+-=()";
    private static final String SUB_TO = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎";

    private MathText() {}

    /** $...$ 부분만 글자로 바꾸고 나머지는 그대로 둔다 */
    static String toPlain(String text) {
        if (text == null || text.indexOf('$') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        StringBuilder math = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
                (math == null ? out : math).append(math == null ? "$" : "\\$");
                i++;
            } else if (c == '$') {
                if (math == null) {
                    math = new StringBuilder();
                } else {
                    out.append(convert(math.toString()));
                    math = null;
                }
            } else {
                (math == null ? out : math).append(c);
            }
        }
        if (math != null) {
            out.append('$').append(math);
        }
        return out.toString();
    }

    /** LaTeX 한 덩어리 변환 */
    static String convert(String tex) {
        return new Parser(tex).parseUntil('\0').trim().replaceAll(" {2,}", " ");
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        String parseUntil(char end) {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == end) {
                    pos++;
                    break;
                }
                if (c == '\\') {
                    sb.append(command());
                } else if (c == '{') {
                    pos++;
                    sb.append(parseUntil('}'));
                } else if (c == '^' || c == '_') {
                    pos++;
                    sb.append(script(atom(), c == '^'));
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            return sb.toString();
        }

        /** {..} 하나 또는 글자 하나 */
        private String atom() {
            while (pos < s.length() && s.charAt(pos) == ' ') {
                pos++;
            }
            if (pos >= s.length()) {
                return "";
            }
            char c = s.charAt(pos);
            if (c == '{') {
                pos++;
                return parseUntil('}');
            }
            if (c == '\\') {
                return command();
            }
            pos++;
            return String.valueOf(c);
        }

        private String command() {
            pos++; // '\'
            if (pos >= s.length()) {
                return "";
            }
            int start = pos;
            if (Character.isLetter(s.charAt(pos))) {
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                    pos++;
                }
            } else {
                pos++;
            }
            String name = s.substring(start, pos);
            switch (name) {
                case "frac", "dfrac", "tfrac" -> {
                    String a = atom();
                    String b = atom();
                    return group(a) + "/" + group(b);
                }
                case "sqrt" -> {
                    String index = "";
                    if (pos < s.length() && s.charAt(pos) == '[') {
                        int close = s.indexOf(']', pos);
                        if (close > 0) {
                            index = s.substring(pos + 1, close);
                            pos = close + 1;
                        }
                    }
                    String body = atom();
                    String root = index.isEmpty() ? "√" : mapAll(index, SUP_FROM, SUP_TO, index + "제곱근") + "√";
                    return root + (body.length() > 1 ? "(" + body + ")" : body);
                }
                case "text", "mathrm", "mathbf", "operatorname" -> {
                    return atom();
                }
                case "overline" -> {
                    return atom() + "̅";
                }
                default -> {
                    String sym = SYMBOLS.get(name);
                    return sym != null ? sym : name;
                }
            }
        }

        private static String group(String x) {
            String t = x.trim();
            return t.matches("[^+\\-*/× ÷]+") || t.length() <= 1 ? t : "(" + t + ")";
        }

        private static String script(String body, boolean sup) {
            String mapped = sup ? mapAll(body, SUP_FROM, SUP_TO, null) : mapAll(body, SUB_FROM, SUB_TO, null);
            if (mapped != null) {
                return mapped;
            }
            return (sup ? "^" : "_") + (body.length() > 1 ? "(" + body + ")" : body);
        }

        private static String mapAll(String body, String from, String to, String fallback) {
            StringBuilder sb = new StringBuilder();
            for (char ch : body.toCharArray()) {
                int i = from.indexOf(ch);
                if (i < 0) {
                    return fallback;
                }
                sb.append(to.charAt(i));
            }
            return sb.toString();
        }
    }
}
