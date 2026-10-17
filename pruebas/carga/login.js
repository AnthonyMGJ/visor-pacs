import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '1');

export const options = {
  vus: VUS,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.USER_NAME, password: __ENV.USER_PASS }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, {
    'login 200': (r) => r.status === 200,
    'trae accessToken': (r) => !!r.json('accessToken'),
  });
  sleep(1);
}
