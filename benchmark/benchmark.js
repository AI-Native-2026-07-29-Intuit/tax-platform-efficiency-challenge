// ============================================================================
// Tax Platform load + correctness benchmark.
//
//   k6 run benchmark/benchmark.js
//   k6 run -e BASE_URL=http://localhost:8080 -e RATE=800 benchmark/benchmark.js
//
// Requests are built from the SAME deterministic rules the seed generates, so
// every request has a matching tax rule. Each response is checked for internal
// correctness (tax == round(subtotal * rate), total == subtotal + tax).
//
// Targets (post cost-cut): throughput > 800 rps, P95 < 500ms, 0 failed checks.
// ============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '400', 10);          // target requests/sec
const DURATION = __ENV.DURATION || '60s';
const CITIES = parseInt(__ENV.CITIES || '1000', 10);      // must match SEED_CITIES
const CUSTOMERS = parseInt(__ENV.CUSTOMERS || '500000', 10);
const PRE_VUS = parseInt(__ENV.PRE_VUS || '200', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '800', 10);

// MUST match db/seed.sql ordinals 0..49 exactly (index = state ordinal).
const STATES = [
  'AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA','HI','ID','IL','IN','IA','KS',
  'KY','LA','ME','MD','MA','MI','MN','MS','MO','MT','NE','NV','NH','NJ','NM','NY',
  'NC','ND','OH','OK','OR','PA','RI','SC','SD','TN','TX','UT','VT','VA','WA','WV','WI','WY',
];
const PRODUCTS = ['SOFTWARE','HARDWARE','FOOD','CLOTHING','SERVICES','DIGITAL_GOODS','MEDICAL','GROCERY'];

const correctness = new Rate('tax_correctness');
const taxRate = new Trend('reported_tax_rate');

export const options = {
  scenarios: {
    constant_load: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    // The challenge targets - k6 marks the run pass/fail against these.
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    tax_correctness: ['rate>0.99'],
  },
};

function randInt(n) {
  return Math.floor(Math.random() * n);
}

function round2(x) {
  return Math.round((x + Number.EPSILON) * 100) / 100;
}

export default function () {
  // cityIndex in [1..CITIES]; its state is deterministic: (cityIndex-1) % 50.
  const cityIndex = 1 + randInt(CITIES);
  const state = STATES[(cityIndex - 1) % 50];
  const city = 'CITY_' + cityIndex;
  const productType = PRODUCTS[randInt(PRODUCTS.length)];
  const customerId = 1 + randInt(CUSTOMERS);
  const amount = round2(10 + Math.random() * 5000);

  const payload = JSON.stringify({ customerId, amount, state, city, productType });
  const res = http.post(`${BASE_URL}/api/tax/calculate`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
  });

  let correct = false;
  if (res.status === 200) {
    try {
      const b = res.json();
      const subtotal = Number(b.subtotal);
      const rate = Number(b.taxRate);
      const tax = Number(b.tax);
      const total = Number(b.total);
      const expectedTax = round2(subtotal * rate);
      correct =
        subtotal === round2(amount) &&
        rate >= 0 && rate <= 1 &&
        Math.abs(tax - expectedTax) <= 0.01 &&
        Math.abs(total - (subtotal + tax)) <= 0.01;
      taxRate.add(rate);
    } catch (e) {
      correct = false;
    }
  }
  correctness.add(correct);
  check(res, { 'tax result correct': () => correct });

  void ok;
}
