# 채점 보정 실험 — 등급을 미리 정한 답변(anchors.json)을 운영과 같은 프롬프트로 채점해 등급이 맞게 나오는지 본다.
# 프롬프트는 GroqService.java에서 그대로 뽑고(SYSTEM_PROMPT + exampleInstruction), 등급 계산도 FeedbackService와 같게 한다.
# 사용: set -a; . ./.env; set +a; python3 run.py groq|gemini [모델] [--level v1] [--set test]
#   → results-<provider>[-<level>][-<set>].jsonl (이어 돌리면 끝난 건 건너뜀)
#   --level vN: level-vN.md를 프롬프트에 덧붙여 LLM이 등급(level)을 직접 판단하고, 서버 규칙(word_cap)으로 상한만 건다
#   --set test: anchors-test.json(검증용, 개발 중엔 안 봄)
import json, os, re, sys, time, urllib.request, urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
PROVIDERS = {
    "groq": ("https://api.groq.com/openai/v1/chat/completions", "GROQ_API_KEY", "openai/gpt-oss-120b", 65),
    "gemini": ("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "GEMINI_API_KEY", "gemini-3.5-flash-lite", 6),
}


def java_string(src, start):
    i = src.index(start) + len(start)
    j = src.index('";\n', i) + 1
    return "".join(json.loads('"' + m + '"') for m in re.findall(r'"((?:\\.|[^"\\])*)"', src[i:j]))


def system_prompt():
    src = open(os.path.join(ROOT, "src/main/java/com/opicnic/opicnic/service/GroqService.java")).read()
    return java_string(src, "SYSTEM_PROMPT =") + java_string(src, "String exampleInstruction =")


# FeedbackService.computeFluencyScore / computeGrade 와 같은 규칙
def fluency(text):
    w = len(text.split())
    return 5 if w >= 130 else 4 if w >= 90 else 3 if w >= 60 else 2 if w >= 30 else 1


def grade(scores):
    s = [x for x in scores if x is not None]
    avg = sum(s) / len(s)
    for g, cut in (("AL", 4.5), ("IH", 3.8), ("IM3", 3.2), ("IM2", 2.6), ("IM1", 2.0)):
        if avg >= cut:
            return g, avg
    return "IL", avg


ORDER = ["IL", "IM1", "IM2", "IM3", "IH", "AL"]
# 서버 안전장치: 짧은 답이 문단 수준 등급을 받지 못하게 상한만 건다(올려 주지는 않는다)
WORD_CAPS = ((35, "IM1"), (60, "IM2"), (90, "IM3"), (120, "IH"))


def word_cap(level, words):
    for limit, cap in WORD_CAPS:
        if words < limit:
            return ORDER[min(ORDER.index(level), ORDER.index(cap))]
    return level


def call(provider, model, system, user):
    url, key_env, _, _ = PROVIDERS[provider]
    body = {"model": model, "temperature": 0.0, "response_format": {"type": "json_object"},
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
    if provider == "groq":
        body |= {"reasoning_effort": "low", "max_tokens": 3000}
    req = urllib.request.Request(url, json.dumps(body).encode(), {
        "Authorization": "Bearer " + os.environ[key_env], "Content-Type": "application/json",
        "User-Agent": "opicnic-calibration/1.0"})
    while True:
        t0 = time.time()
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                return json.load(r), time.time() - t0
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                wait = float(e.headers.get("retry-after") or 30)
                print(f"   {e.code} → {wait:.0f}s 대기", flush=True)
                time.sleep(wait + 1)
                continue
            raise RuntimeError(f"{e.code} {e.read()[:300]}")


def main():
    args = sys.argv[1:]
    opt = lambda name: args[args.index(name) + 1] if name in args else None
    level_tag, set_tag = opt("--level"), opt("--set")
    pos = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or not args[i - 1].startswith("--"))]
    provider = pos[0]
    model = pos[1] if len(pos) > 1 else PROVIDERS[provider][2]
    gap = PROVIDERS[provider][3]
    data = json.load(open(os.path.join(HERE, f"anchors-{set_tag}.json" if set_tag else "anchors.json")))
    out_path = os.path.join(HERE, "-".join(["results", provider] + [t for t in (level_tag, set_tag) if t]) + ".jsonl")
    done = set()
    if os.path.exists(out_path):
        done = {(r["q"], r["level"]) for r in map(json.loads, open(out_path)) if r.get("model") == model and "error" not in r}
    system = system_prompt()
    if level_tag:
        system += "\n\n" + open(os.path.join(HERE, f"level-{level_tag}.md")).read()
    for a in data["anchors"]:
        if (a["q"], a["level"]) in done:
            continue
        q = data["questions"][a["q"]]
        user = f"문제 유형: {q['type']}\n질문: {q['content']}\n사용자 응답: {a['text']}"
        rec = {"model": model, "q": a["q"], "level": a["level"], "words": len(a["text"].split())}
        try:
            resp, secs = call(provider, model, system, user)
            raw = resp["choices"][0]["message"]["content"]
            rec |= {"secs": round(secs, 1), "usage": resp.get("usage"), "raw": raw}
            fb = json.loads(raw)
            sc = {k: fb.get(k + "Score") for k in ("mainPoint", "expression", "accuracy", "content")}
            sc = {k: int(v) if isinstance(v, (int, float, str)) and str(v).strip().lstrip("-").isdigit() else None for k, v in sc.items()}
            g, avg = grade([sc["mainPoint"], sc["expression"], sc["accuracy"], fluency(a["text"]), sc["content"]])
            rec |= {"scores": sc, "fluency": fluency(a["text"]), "grade": g, "avg": round(avg, 2), "json_ok": True}
            if level_tag:
                llm = str(fb.get("level", "")).strip().upper()
                rec |= {"score_grade": g, "llm_level": llm,
                        "grade": word_cap(llm, rec["words"]) if llm in ORDER else None}
        except json.JSONDecodeError:
            rec |= {"json_ok": False}
        except Exception as e:
            rec |= {"error": str(e)[:300]}
        with open(out_path, "a") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        print(f"{a['q']:5} {a['level']:3} → {rec.get('grade', rec.get('error', 'JSON 실패'))} {('LLM ' + rec['llm_level']) if rec.get('llm_level') else rec.get('scores', '')} {rec.get('secs', '')}s", flush=True)
        time.sleep(gap)


if __name__ == "__main__":
    main()
