import http from 'k6/http';
import { check } from 'k6';

// 스트레스 테스트: 읽기 부하를 계단식으로 올리며 한계점(knee)을 찾는다.
// 각 레벨을 lvl 태그로 분리 → 요약 JSON에서 레벨별 p95/p99/에러/처리량 산출.
//   k6 run --summary-export=stress.json k6/stress.js
const BASE = __ENV.BASE_URL || 'http://localhost:18090';
const LEVELS = (__ENV.LEVELS || '50,100,200,400,700,1000').split(',').map((n) => parseInt(n, 10));
const HOLD = parseInt(__ENV.HOLD || '20', 10); // 레벨당 유지 시간(s)

function buildScenarios() {
  const s = {};
  LEVELS.forEach((vus, i) => {
    s[`L${vus}`] = {
      executor: 'constant-vus', exec: 'reads', vus,
      duration: `${HOLD}s`, startTime: `${i * HOLD}s`,
      tags: { lvl: String(vus).padStart(4, '0') },
    };
  });
  return s;
}

// 레벨별 서브메트릭 생성을 위해 (항상 통과하는) 임계값을 건다 → 요약 JSON에 레벨별 값이 남는다.
function buildThresholds() {
  const t = { checks: ['rate>0.90'] };
  LEVELS.forEach((v) => {
    const l = String(v).padStart(4, '0');
    t[`http_req_duration{lvl:${l}}`] = ['p(95)>=0'];
    t[`http_req_failed{lvl:${l}}`] = ['rate>=0'];
    t[`http_reqs{lvl:${l}}`] = ['count>=0'];
  });
  return t;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildThresholds(),
};

function pick(a) { return a[Math.floor(Math.random() * a.length)]; }

export function setup() {
  const tokens = [];
  for (let i = 0; i < 20; i++) {
    const r = http.post(`${BASE}/api/v1/auth/guest`);
    if (r.status === 200) tokens.push(r.json('data.accessToken'));
  }
  const el = http.get(`${BASE}/api/v1/exhibitions?page=0&size=30`, { headers: { Authorization: `Bearer ${tokens[0]}` } });
  const ids = (el.json('data.content') || []).map((e) => e.exhibitionId).filter(Boolean);
  console.log(`setup: tokens=${tokens.length}, exhibitionIds=${ids.length}, levels=${LEVELS}`);
  return { tokens, ids };
}

// 홈/브라우징 읽기 믹스 (sleep 없음 = 최대 압박)
export function reads(data) {
  const H = { headers: { Authorization: `Bearer ${pick(data.tokens)}` } };
  check(http.get(`${BASE}/api/v1/exhibitions?page=0&size=20`, { ...H, tags: { name: 'exhibitions_list' } }), { 'exhibitions_list 200': (x) => x.status === 200 });
  check(http.get(`${BASE}/api/v1/exhibitions/${pick(data.ids)}`, { ...H, tags: { name: 'exhibition_detail' } }), { 'exhibition_detail 200': (x) => x.status === 200 });
  check(http.get(`${BASE}/api/v1/reminds/candidate`, { ...H, tags: { name: 'remind_candidate' } }), { 'remind_candidate 200': (x) => x.status === 200 });
  check(http.get(`${BASE}/api/v1/records?page=0&size=20`, { ...H, tags: { name: 'record_list' } }), { 'record_list 200': (x) => x.status === 200 });
}
