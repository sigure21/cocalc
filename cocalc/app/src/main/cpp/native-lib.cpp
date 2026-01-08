#include <jni.h>
#include <string>
#include <cctype>
#include <cmath>
#include <cstdio>

static void skipSpaces(const std::string& s, size_t& i) {
    while (i < s.size() && std::isspace(static_cast<unsigned char>(s[i]))) i++;
}

static bool parseNumber(const std::string& s, size_t& i, double& out) {
    skipSpaces(s, i);
    if (i >= s.size()) return false;

    size_t start = i;
    bool hasDigit = false;

    while (i < s.size() && std::isdigit(static_cast<unsigned char>(s[i]))) {
        i++;
        hasDigit = true;
    }

    if (i < s.size() && s[i] == '.') {
        i++;
        while (i < s.size() && std::isdigit(static_cast<unsigned char>(s[i]))) {
            i++;
            hasDigit = true;
        }
    }

    if (!hasDigit) return false;

    try {
        out = std::stod(s.substr(start, i - start));
        return true;
    } catch (...) {
        return false;
    }
}

// Grammar (recursive descent, precedence):
// expr  := term ((+|-) term)*
// term  := factor ((*|/) factor)*
// factor:= (+|-) factor | number | '(' expr ')'

static bool parseExpr(const std::string& s, size_t& i, double& out);

static bool parseFactor(const std::string& s, size_t& i, double& out) {
    skipSpaces(s, i);
    if (i >= s.size()) return false;

    // unary +/-
    if (s[i] == '+' || s[i] == '-') {
        char sign = s[i];
        i++;
        double val = 0.0;
        if (!parseFactor(s, i, val)) return false;
        out = (sign == '-') ? -val : val;
    }
        // parentheses
    else if (s[i] == '(') {
        i++;
        double val = 0.0;
        if (!parseExpr(s, i, val)) return false;
        skipSpaces(s, i);
        if (i >= s.size() || s[i] != ')') return false;
        i++;
        out = val;
    }
        // number
    else {
        if (!parseNumber(s, i, out)) return false;
    }

    // ✅ postfix percent: factor% == factor / 100
    while (true) {
        skipSpaces(s, i);
        if (i < s.size() && s[i] == '%') {
            i++;
            out /= 100.0;
            continue;
        }
        break;
    }

    return true;
}


static bool parseTerm(const std::string& s, size_t& i, double& out) {
    double left = 0.0;
    if (!parseFactor(s, i, left)) return false;

    while (true) {
        skipSpaces(s, i);
        if (i >= s.size()) break;

        char op = s[i];
        if (op != '*' && op != '/') break;

        i++;
        double right = 0.0;
        if (!parseFactor(s, i, right)) return false;

        if (op == '*') {
            left *= right;
        } else {
            if (std::fabs(right) < 1e-12) return false; // division by zero
            left /= right;
        }
    }

    out = left;
    return true;
}

static bool parseExpr(const std::string& s, size_t& i, double& out) {
    double left = 0.0;
    if (!parseTerm(s, i, left)) return false;

    while (true) {
        skipSpaces(s, i);
        if (i >= s.size()) break;

        char op = s[i];
        if (op != '+' && op != '-') break;

        i++;
        double right = 0.0;
        if (!parseTerm(s, i, right)) return false;

        if (op == '+') left += right;
        else left -= right;
    }

    out = left;
    return true;
}

static std::string formatDouble(double v) {
    // 정수면 .0 제거
    if (std::fabs(v - std::round(v)) < 1e-10) {
        long long iv = static_cast<long long>(std::llround(v));
        return std::to_string(iv);
    }

    // 소수는 너무 길게 나오지 않게 정리
    char buf[64];
    std::snprintf(buf, sizeof(buf), "%.10g", v);
    return std::string(buf);
}

/**
 * 괄호 자동 보정 규칙:
 * - ')'가 '('보다 먼저 많아지는 순간(balance < 0) -> ERR
 * - '('가 남아 있으면 수식 맨 뒤에 ')'를 balance만큼 자동 추가
 */
static bool normalizeParens(std::string& s) {
    int balance = 0;
    for (char c : s) {
        if (c == '(') balance++;
        else if (c == ')') {
            balance--;
            if (balance < 0) return false; // extra ')'
        }
    }
    while (balance > 0) {
        s.push_back(')');
        balance--;
    }
    return true;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_cocalc_MainActivity_evalExpression(JNIEnv* env, jobject /*thiz*/, jstring jexpr) {
    const char* cexpr = env->GetStringUTFChars(jexpr, nullptr);
    std::string expr = (cexpr ? cexpr : "");
    env->ReleaseStringUTFChars(jexpr, cexpr);

    // 괄호 자동 보정 적용
    if (!normalizeParens(expr)) {
        return env->NewStringUTF("ERR");
    }

    size_t i = 0;
    double result = 0.0;

    bool ok = parseExpr(expr, i, result);
    skipSpaces(expr, i);

    // 전체를 다 소비했는지 체크 (중간에 쓰레기 문자 있으면 실패)
    if (!ok || i != expr.size()) {
        return env->NewStringUTF("ERR");
    }

    std::string out = formatDouble(result);
    return env->NewStringUTF(out.c_str());
}

} // extern "C"

