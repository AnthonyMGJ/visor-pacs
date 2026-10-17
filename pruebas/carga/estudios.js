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

export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.USER_NAME, password: __ENV.USER_PASS }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`Login fallo en setup: ${res.status}`);
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const res = http.get(`${BASE}/api/dicomweb/studies`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(res, { 'lista 200': (r) => r.status === 200 });
  sleep(1);
}
