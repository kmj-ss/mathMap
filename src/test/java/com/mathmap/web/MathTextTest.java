package com.mathmap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MathTextTest {

    @Test
    void convertsCommonExpressions() {
        assertEquals("3/4", MathText.toPlain("$\\dfrac{3}{4}$"));
        assertEquals("x²", MathText.toPlain("$x^{2}$"));
        assertEquals("√2", MathText.toPlain("$\\sqrt{2}$"));
        assertEquals("√(x+1)", MathText.toPlain("$\\sqrt{x+1}$"));
        assertEquals("a₁", MathText.toPlain("$a_{1}$"));
        assertEquals("(x+1)/2", MathText.toPlain("$\\frac{x+1}{2}$"));
        assertEquals("3 × 4 ÷ 2", MathText.toPlain("$3 \\times 4 \\div 2$"));
        assertEquals("∛8", MathText.toPlain("$\\sqrt[3]{8}$").replace("³√", "∛"));
        assertEquals("x^(ab)", MathText.toPlain("$x^{ab}$"));
    }

    @Test
    void keepsTextOutsideFormula() {
        assertEquals("다음을 계산하시오: 3/4 + 1/4 = ?", MathText.toPlain("다음을 계산하시오: $\\dfrac{3}{4} + \\dfrac{1}{4}$ = ?"));
        assertEquals("가격은 $5", MathText.toPlain("가격은 \\$5"));
        assertEquals("수식 없음", MathText.toPlain("수식 없음"));
    }
}
