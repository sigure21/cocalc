package com.example.cocalc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var etExpression: EditText
    private var expr: String = ""

    // "=" 직후 상태인지 여부 (결과 표시 상태)
    private var justEvaluated = false

    // JNI
    private external fun evalExpression(expression: String): String

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etExpression = findViewById(R.id.etExpression)

        // expr이 비어있을 때 화면에 0처럼 보이게 (텍스트는 빈 문자열 유지)
        etExpression.hint = "0"

        // 키보드/입력 막고 커서 이동만 허용(레이아웃에서 설정했더라도 안전하게)
        etExpression.showSoftInputOnFocus = false

        // 숫자 버튼
        listOf(
            R.id.btn0 to "0",
            R.id.btn1 to "1",
            R.id.btn2 to "2",
            R.id.btn3 to "3",
            R.id.btn4 to "4",
            R.id.btn5 to "5",
            R.id.btn6 to "6",
            R.id.btn7 to "7",
            R.id.btn8 to "8",
            R.id.btn9 to "9"
        ).forEach { (id, v) ->
            findViewById<Button>(id).setOnClickListener { appendNumber(v) }
        }

        // .
        findViewById<Button>(R.id.btnDot).setOnClickListener { appendDot() }

        // 연산자
        findViewById<Button>(R.id.btnPlus).setOnClickListener { appendOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { appendOperator("-") }
        findViewById<Button>(R.id.btnMul).setOnClickListener { appendOperator("*") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendOperator("/") }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { appendPercent( ) }


        // ()
        findViewById<Button>(R.id.btnParen).setOnClickListener { appendParenSmart() }

        // C
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            expr = ""
            justEvaluated = false
            render(0)
        }

        // ← (커서 기준)
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            deleteBeforeCursor()
            justEvaluated = false
        }

        // =
        findViewById<Button>(R.id.btnEq).setOnClickListener {
            if (expr.isBlank()) return@setOnClickListener

            val last = expr.last()
            if (isOperator(last) || last == '.') {
                showError("수식이 완성되지 않았습니다.")
                return@setOnClickListener
            }

            val result = evalExpression(expr)
            if (result == "ERR") {
                // ✅ 수식 유지 + 알림만
                showError("수식 오류 (괄호 / 연산자 / 0으로 나눔)")
                return@setOnClickListener
            }

            expr = result
            justEvaluated = true
            render(expr.length)
        }

        render(0)
    }

    /* ================= UI 렌더 ================= */

    private fun render(cursorPos: Int) {
        // expr와 EditText 텍스트를 항상 동일하게 유지
        etExpression.setText(expr)

        // cursor를 항상 expr 범위로 클램프
        val safeCursor = cursorPos.coerceIn(0, expr.length)
        etExpression.setSelection(safeCursor)
    }

    private fun showError(msg: String) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
    }

    /* ================= 입력 처리 ================= */

    private fun safeCursor(): Int {
        // selectionStart가 -1로 들어오는 환경도 있어서 방어
        val c = etExpression.selectionStart
        return c.coerceIn(0, expr.length)
    }

    private fun insertText(text: String) {
        val cursor = safeCursor()
        expr = expr.substring(0, cursor) + text + expr.substring(cursor)
        render(cursor + text.length)
    }

    private fun deleteBeforeCursor() {
        val cursor = safeCursor()
        if (cursor <= 0) return
        expr = expr.removeRange(cursor - 1, cursor)
        render(cursor - 1)
    }

    private fun appendNumber(n: String) {
        // ✅ 결과 직후 숫자 입력이면 새 수식 시작
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
            render(0)
        }
        insertText(n)
    }

    private fun appendDot() {
        // ✅ 결과 직후 '.' 입력이면 새 수식 시작
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
            render(0)
        }

        val cursor = safeCursor()
        val left = expr.substring(0, cursor)
        val lastToken = left.takeLastWhile { it.isDigit() || it == '.' }
        if (lastToken.contains('.')) return

        // 커서 위치 기준 직전이 연산자/여는괄호/비어있으면 "0." 형태가 UX상 더 자연스러움
        val prev = left.lastOrNull()
        if (prev == null || isOperator(prev) || prev == '(') {
            insertText("0.")
        } else {
            insertText(".")
        }
    }

    private fun appendOperator(op: String) {
        // ✅ 결과 직후 연산자는 이어서 계산 가능
        justEvaluated = false

        if (expr.isBlank()) {
            if (op == "-") insertText("-") // unary minus
            return
        }

        val cursor = safeCursor()
        val left = expr.substring(0, cursor)
        val prev = left.lastOrNull() ?: return

        // 연산자 연속 입력이면 직전 연산자 교체
        if (isOperator(prev)) {
            expr = expr.removeRange(cursor - 1, cursor)
            render(cursor - 1)
            insertText(op)
            return
        }

        // '.' 뒤면 0 보정
        if (prev == '.') {
            insertText("0$op")
            return
        }

        // '(' 바로 뒤에 +,*,/는 막고 (unary '-'는 허용)
        if (prev == '(' && op != "-") return

        insertText(op)
    }

    private fun appendParenSmart() {
        // ✅ 결과 직후 괄호는 이어서 계산 가능
        justEvaluated = false

        val cursor = safeCursor()
        val left = expr.substring(0, cursor)

        // 커서 기준으로 앞부분에서만 괄호 밸런스를 판단(중간 편집 대응)
        val openCount = left.count { it == '(' }
        val closeCount = left.count { it == ')' }
        val prev = left.lastOrNull()

        val canClose = (openCount > closeCount) && (prev?.isDigit() == true || prev == ')')

        if (canClose) {
            insertText(")")
            return
        }

        // '(' 추가
        // 숫자나 ')' 다음 '('는 암묵적 곱셈
        if (prev?.isDigit() == true || prev == ')') {
            insertText("*(")
        } else {
            insertText("(")
        }
    }
    private fun appendPercent() {
        // 결과 직후 %는 이어서 수식 계속 가능 (예: 53% 같은 경우)
        justEvaluated = false

        if (expr.isBlank()) return

        val cursor = safeCursor()
        val left = expr.substring(0, cursor)
        val prev = left.lastOrNull() ?: return

        // 숫자나 ')' 뒤에서만 % 허용 (예: 85% 또는 (1+2)%)
        if (prev.isDigit() || prev == ')') {
            insertText("%")
        } else {
            showError("%는 숫자 또는 ')' 뒤에서만 사용할 수 있습니다.")
        }
    }

    private fun isOperator(c: Char): Boolean {
        return c == '+' || c == '-' || c == '*' || c == '/'
    }
}
