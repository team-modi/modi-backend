import http from 'k6/http';
import { check } from 'k6';

// 부하 테스트: 읽기(홈/브라우징) 램핑 + 쓰기(기록·리마인드 생성) 일정 부하 동시.
//   k6 run k6/load.js                       (기본 http://localhost:18090)
//   k6 run -e BASE_URL=... -e PEAK=150 k6/load.js
const BASE = __ENV.BASE_URL || 'http://localhost:18090';
const PEAK = parseInt(__ENV.PEAK || '100', 10); // 읽기 최대 동시 VU
const WRITE_RPS = parseInt(__ENV.WRITE_RPS || '5', 10);

export const options = {
  scenarios: {
    // 읽기 부하: 20 → 50 → PEAK 단계적 램핑(용량 곡선 확인)
    reads: {
      executor: 'ramping-vus', exec: 'reads', startVUs: 0,
      stages: [
        { duration: '15s', target: 20 },
        { duration: '20s', target: 20 },
        { duration: '15s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '15s', target: PEAK },
        { duration: '20s', target: PEAK },
        { duration: '10s', target: 0 },
      ],
      tags: { scn: 'reads' },
    },
    // 쓰기 부하: 초당 WRITE_RPS건 일정(기록 생성→리마인드 저장)
    writes: {
      executor: 'constant-arrival-rate', exec: 'writes',
      rate: WRITE_RPS, timeUnit: '1s', duration: '105s',
      preAllocatedVUs: 10, maxVUs: 40,
      tags: { scn: 'writes' },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    'http_req_failed{scn:reads}': ['rate<0.01'],
    'http_req_duration{scn:reads}': ['p(95)<500', 'p(99)<1500'],
    'http_req_failed{scn:writes}': ['rate<0.02'],
    'http_req_duration{scn:writes}': ['p(95)<2000'],
  },
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
  console.log(`setup: tokens=${tokens.length}, exhibitionIds=${ids.length}`);
  return { tokens, ids };
}

// 홈/브라우징 읽기 믹스
export function reads(data) {
  const H = { headers: { Authorization: `Bearer ${pick(data.tokens)}` } };
  const a = http.get(`${BASE}/api/v1/exhibitions?page=0&size=20`, { ...H, tags: { name: 'exhibitions_list' } });
  check(a, { 'exhibitions_list 200': (x) => x.status === 200 });
  const b = http.get(`${BASE}/api/v1/exhibitions/${pick(data.ids)}`, { ...H, tags: { name: 'exhibition_detail' } });
  check(b, { 'exhibition_detail 200': (x) => x.status === 200 });
  const c = http.get(`${BASE}/api/v1/reminds/candidate`, { ...H, tags: { name: 'remind_candidate' } });
  check(c, { 'remind_candidate 200': (x) => x.status === 200 });
  const d = http.get(`${BASE}/api/v1/records?page=0&size=20`, { ...H, tags: { name: 'record_list' } });
  check(d, { 'record_list 200': (x) => x.status === 200 });
}

// 쓰기: 기록 생성 → 리마인드 저장
export function writes(data) {
  const g = http.post(`${BASE}/api/v1/auth/guest`, null, { tags: { name: 'guest_login' } });
  if (g.status !== 200) return;
  const H = { headers: { Authorization: `Bearer ${g.json('data.accessToken')}`, 'Content-Type': 'application/json' } };
  const body = JSON.stringify({
    exhibitionId: pick(data.ids), writeMode: 'DIRECT', viewedAt: '2026-06-15',
    content: 'load test 기록', emotionCodes: ['평화로운'], media: [],
  });
  const c = http.post(`${BASE}/api/v1/records`, body, { ...H, tags: { name: 'record_create' } });
  check(c, { 'record_create 201': (x) => x.status === 201 });
  if (c.status === 201) {
    const rid = c.json('data.recordId');
    const s = http.post(`${BASE}/api/v1/reminds`,
      JSON.stringify({ recordId: rid, emotionCodes: ['슬픈'], reflection: 'load test 회고' }),
      { ...H, tags: { name: 'remind_save' } });
    check(s, { 'remind_save 200': (x) => x.status === 200 });
  }
}
