import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 실행: k6 run k6/e2e.js  (필요 시 BASE_URL, VUS, ITER 환경변수로 조절)
//   k6 run -e BASE_URL=http://localhost:18090 -e VUS=3 -e ITER=1 k6/e2e.js
const BASE = __ENV.BASE_URL || 'http://localhost:18090';
const VUS = parseInt(__ENV.VUS || '3', 10);
const ITER = parseInt(__ENV.ITER || '1', 10);

const journeyErrors = new Counter('journey_errors');

export const options = {
  scenarios: {
    e2e_journey: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITER, // VU당 반복. 리마인드 저장은 실 AI 호출(느림·유료)일 수 있어 기본은 가볍게.
      maxDuration: '3m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'], // 모든 기능 체크 99%+ 통과
    journey_errors: ['count==0'],
    'http_req_duration{name:health}': ['p(95)<800'],
    'http_req_duration{name:exhibitions_list}': ['p(95)<2000'],
  },
};

// 4xx가 '의도된' 요청(인증 가드)은 실패로 세지 않도록 개별 처리하고,
// 나머지는 각 check로 검증한다.
function jsonGet(url, params) {
  return http.get(url, params);
}

function must(res, name, conds) {
  const ok = check(res, conds);
  if (!ok) {
    journeyErrors.add(1);
    console.error(`[FAIL] ${name} status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
  return ok;
}

export default function () {
  let token, exhibitionId, recordId, remindId;

  group('01 health', () => {
    const r = http.get(`${BASE}/actuator/health`, { tags: { name: 'health' } });
    must(r, 'health', { 'health 200': (x) => x.status === 200, 'status UP': (x) => x.json('status') === 'UP' });
  });

  group('02 auth: guest login', () => {
    const r = http.post(`${BASE}/api/v1/auth/guest`, null, { tags: { name: 'guest_login' } });
    must(r, 'guest_login', {
      'guest 200': (x) => x.status === 200,
      'has accessToken': (x) => !!x.json('data.accessToken'),
      'provider guest': (x) => x.json('data.user.provider') === 'guest',
    });
    token = r.json('data.accessToken');
  });

  const AUTH = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };

  group('03 exhibitions: list', () => {
    const r = jsonGet(`${BASE}/api/v1/exhibitions?page=0&size=5`, { ...AUTH, tags: { name: 'exhibitions_list' } });
    must(r, 'exhibitions_list', {
      'exh 200': (x) => x.status === 200,
      'exh has content': (x) => (x.json('data.content') || []).length > 0,
    });
    exhibitionId = r.json('data.content.0.exhibitionId');
  });

  group('04 record: create + read', () => {
    const body = JSON.stringify({
      exhibitionId, writeMode: 'DIRECT', viewedAt: '2026-06-15',
      content: 'k6 E2E 기록 — 고요한 전시장에서 오래 머물렀다.',
      emotionCodes: ['평화로운', '고요한'], media: [],
    });
    const c = http.post(`${BASE}/api/v1/records`, body, { ...AUTH, tags: { name: 'record_create' } });
    must(c, 'record_create', {
      'record 201': (x) => x.status === 201, // 이 프로젝트는 기록 생성만 201
      'recordId': (x) => !!x.json('data.recordId'),
    });
    recordId = c.json('data.recordId');

    const d = jsonGet(`${BASE}/api/v1/records/${recordId}`, { ...AUTH, tags: { name: 'record_detail' } });
    must(d, 'record_detail', {
      'record detail 200': (x) => x.status === 200,
      'content 반영': (x) => String(x.json('data.content')).includes('k6 E2E'),
    });

    const l = jsonGet(`${BASE}/api/v1/records?page=0&size=20`, { ...AUTH, tags: { name: 'record_list' } });
    must(l, 'record_list', { 'record list 200': (x) => x.status === 200, 'list >=1': (x) => x.json('data.totalElements') >= 1 });
  });

  group('05 remind: candidate', () => {
    const r = jsonGet(`${BASE}/api/v1/reminds/candidate`, { ...AUTH, tags: { name: 'remind_candidate' } });
    // 방금 만든 기록은 7일 미만이라 보통 data:null 이 정상.
    must(r, 'remind_candidate', { 'candidate 200': (x) => x.status === 200 });
  });

  group('06 remind: save + detail + list', () => {
    const body = JSON.stringify({ recordId, emotionCodes: ['슬픈', '강렬한'], reflection: 'k6 E2E 회고 — 다시 보니 먹먹하다.' });
    const s = http.post(`${BASE}/api/v1/reminds`, body, { ...AUTH, tags: { name: 'remind_save' } });
    must(s, 'remind_save', {
      'remind save 200': (x) => x.status === 200,
      'before 존재': (x) => x.json('data.before') !== undefined,
      'after.text 존재': (x) => !!x.json('data.after.text'),
      'aiStatus 유효': (x) => ['READY', 'SKIPPED', 'FAILED'].includes(x.json('data.aiStatus')),
    });
    remindId = s.json('data.remindId');

    const d = jsonGet(`${BASE}/api/v1/reminds/${remindId}`, { ...AUTH, tags: { name: 'remind_detail' } });
    must(d, 'remind_detail', { 'remind detail 200': (x) => x.status === 200, 'remindId 일치': (x) => x.json('data.remindId') === remindId });

    const l = jsonGet(`${BASE}/api/v1/reminds?page=0&size=20`, { ...AUTH, tags: { name: 'remind_list' } });
    must(l, 'remind_list', { 'remind list 200': (x) => x.status === 200, 'remind list >=1': (x) => x.json('data.totalElements') >= 1 });
  });

  group('07 auth guard', () => {
    // access 토큰은 쿠키로도 전달된다(게스트 로그인이 access_token 쿠키를 심음).
    // k6 VU 쿠키 저장소에 남은 쿠키를 지워야 '진짜 토큰 없음'을 검증할 수 있다.
    const jar = http.cookieJar();
    jar.delete(BASE, 'access_token');
    jar.delete(BASE, 'refresh_token');
    const r = http.get(`${BASE}/api/v1/reminds/candidate`, { tags: { name: 'remind_noauth' } });
    must(r, 'remind_noauth', { 'no-token 401': (x) => x.status === 401, 'NO_ACCESS_TOKEN': (x) => x.json('meta.errorCode') === 'NO_ACCESS_TOKEN' });
  });

  sleep(1);
}
