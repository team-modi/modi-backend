#!/usr/bin/env python3
"""Metabase 초기 구성 자동화(재현용): 관리자 생성 → MySQL(읽기전용) 연결 → 사용자 지표 질문·대시보드.

새 Metabase 인스턴스(로컬/EC2)에서 1회 실행하면 동일한 대시보드가 만들어진다.
자격증명은 환경변수로 받는다(하드코딩 없음):

  MB_URL          Metabase 주소 (기본 http://localhost:3001)
  MB_ADMIN_EMAIL  관리자 이메일
  MB_ADMIN_PW     관리자 비밀번호(강한 값)
  MB_DB_HOST      앱 MySQL 호스트 (기본 mysql — 같은 compose 네트워크)
  MB_DB_NAME      앱 DB (기본 mydatabase)
  MB_RO_USER      읽기전용 계정 (기본 metabase_ro)
  MB_RO_PW        읽기전용 계정 비밀번호

실행:  MB_ADMIN_EMAIL=you@ex.com MB_ADMIN_PW='...' MB_RO_PW='...' python3 observability/metabase-setup.py
"""
import json, os, sys, time, urllib.request, urllib.error

MB = os.environ.get("MB_URL", "http://localhost:3001")
ADMIN_EMAIL = os.environ["MB_ADMIN_EMAIL"]
ADMIN_PW = os.environ["MB_ADMIN_PW"]
DB = {
    "host": os.environ.get("MB_DB_HOST", "mysql"),
    "port": int(os.environ.get("MB_DB_PORT", "3306")),
    "dbname": os.environ.get("MB_DB_NAME", "mydatabase"),
    "user": os.environ.get("MB_RO_USER", "metabase_ro"),
    "password": os.environ["MB_RO_PW"],
    "ssl": False, "tunnel-enabled": False,
}


def call(method, path, body=None, session=None):
    req = urllib.request.Request(MB + path,
                                 data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    if session:
        req.add_header("X-Metabase-Session", session)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else {})
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode()[:300]}


# 1) 셋업 or 로그인
_, props = call("GET", "/api/session/properties")
if props.get("setup-token"):
    st, res = call("POST", "/api/setup", {
        "token": props["setup-token"],
        "user": {"first_name": "Admin", "last_name": "Modi", "email": ADMIN_EMAIL,
                 "password": ADMIN_PW, "site_name": "Modi Analytics"},
        "prefs": {"site_name": "Modi Analytics", "allow_tracking": False},
    })
    if st not in (200, 201):
        print("setup 실패:", st, res); sys.exit(1)
    session = res["id"]
    print("✅ 관리자 생성")
else:
    st, res = call("POST", "/api/session", {"username": ADMIN_EMAIL, "password": ADMIN_PW})
    if st != 200:
        print("로그인 실패(이미 셋업됨, 자격 불일치):", st, res); sys.exit(1)
    session = res["id"]
    print("✅ 로그인(기존 인스턴스)")

# 2) MySQL(읽기전용) 연결 — 이미 있으면 재사용
st, dbs = call("GET", "/api/database", session=session)
lst = dbs.get("data", dbs) if isinstance(dbs, dict) else dbs
db_id = next((d["id"] for d in lst if d.get("engine") == "mysql"), None)
if db_id is None:
    st, r = call("POST", "/api/database",
                 {"engine": "mysql", "name": "modi (app)", "details": DB}, session=session)
    if st not in (200, 201):
        print("DB 연결 실패:", st, r.get("error", "")[:200]); sys.exit(1)
    db_id = r["id"]
    print("✅ MySQL 연결(id=%s)" % db_id)
    call("POST", "/api/database/%s/sync_schema" % db_id, session=session)
    time.sleep(8)
else:
    print("ℹ️ MySQL 이미 연결됨(id=%s)" % db_id)

# 3) 질문(카드) — 이미 있으면 건너뜀
_, existing = call("GET", "/api/card", session=session)
have = {c["name"] for c in (existing if isinstance(existing, list) else [])}


def card(name, sql, display, size):
    if name in have:
        print("   · 이미 있음:", name); return None
    st, r = call("POST", "/api/card", {"name": name, "display": display,
                 "dataset_query": {"type": "native", "native": {"query": sql}, "database": db_id},
                 "visualization_settings": {}}, session=session)
    if st in (200, 201):
        print("   ✅", name); return {"id": r["id"], "size": size}
    print("   ⚠️", name, st, r.get("error", "")[:100]); return None


defs = [
    ("총 사용자 수", "SELECT COUNT(*) AS `사용자` FROM users", "scalar", (6, 4)),
    ("총 기록 수", "SELECT COUNT(*) AS `기록` FROM records WHERE deleted_at IS NULL", "scalar", (6, 4)),
    ("리마인드 전환율(%)",
     "SELECT ROUND(100*SUM(CASE WHEN r.record_id IS NOT NULL THEN 1 ELSE 0 END)/COUNT(*),1) AS `전환율` "
     "FROM records rec LEFT JOIN (SELECT DISTINCT record_id FROM reminds WHERE deleted_at IS NULL) r "
     "ON r.record_id=rec.id WHERE rec.deleted_at IS NULL AND rec.created_at <= NOW()-INTERVAL 7 DAY", "scalar", (6, 4)),
    ("신규 가입 추이", "SELECT DATE(created_at) AS `날짜`, COUNT(*) AS `가입` FROM users GROUP BY `날짜` ORDER BY `날짜`", "line", (12, 7)),
    ("기록 생성 추이", "SELECT DATE(created_at) AS `날짜`, COUNT(*) AS `기록수` FROM records WHERE deleted_at IS NULL GROUP BY `날짜` ORDER BY `날짜`", "line", (12, 7)),
    ("리마인드 AI 상태 분포", "SELECT ai_status AS `상태`, COUNT(*) AS `건수` FROM reminds WHERE deleted_at IS NULL GROUP BY `상태`", "bar", (8, 7)),
    ("인기 감정 Top 10", "SELECT emotion_code AS `감정`, COUNT(*) AS `건수` FROM record_emotions GROUP BY `감정` ORDER BY `건수` DESC LIMIT 10", "row", (16, 7)),
]
cards = [c for c in (card(*d) for d in defs) if c]

# 4) 대시보드 — 없으면 생성 + 배치
if cards:
    st, dash = call("POST", "/api/dashboard", {"name": "Modi 사용자 지표"}, session=session)
    if st in (200, 201):
        did = dash["id"]
        dcs, col, row, rh = [], 0, 0, 0
        for i, c in enumerate(cards):
            w, h = c["size"]
            if col + w > 24:
                col = 0; row += rh; rh = 0
            dcs.append({"id": -(i + 1), "card_id": c["id"], "row": row, "col": col, "size_x": w, "size_y": h})
            col += w; rh = max(rh, h)
        call("PUT", "/api/dashboard/%s" % did, {"dashcards": dcs}, session=session)
        print("✅ 대시보드 'Modi 사용자 지표' → %s/dashboard/%s" % (MB, did))
print("완료.")
