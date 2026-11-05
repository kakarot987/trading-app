import { test, expect } from '@playwright/test';

const walletId = '8526fb2f-ccf1-487b-9f4e-0094b412b45e';
const prefix = `/api/v1/paper/wallets/${walletId}`;

const sampleBalance = [{ asset: 'USDT', available: '100' }];
const sampleOrders = [{
  orderId: '12a924ca-3178-4ff0-824e-bc0f73c8433b',
  clientOrderId: 'test-order',
  symbol: 'BTCUSDT',
  side: 'BUY',
  quantity: '0.01',
  submittedAtMs: 1793400000000,
  status: 'NEW',
  rejectionReason: null,
}];
const sampleEvaluation = {
  activity: {
    fillCount: 0,
    tradedNotionalUsdt: '0',
    paidFeesUsdt: '0',
    quoteLedgerNetUsdt: '0',
  },
  evidence: [],
  limitations: [
    'Paper trades only; no live execution or externally verified performance.',
    'Returns and drawdown unavailable: validated chronological equity marks required.',
  ],
  inventory: null,
  equity: null,
  intervalRisk: null,
  benchmark: null,
};

test('paper portfolio renders persisted-looking mock data with GET-only requests and refresh', async ({ page }) => {
  const requests = [];
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.route('**/api/v1/paper/**', async (route) => {
    const request = route.request();
    requests.push({ method: request.method(), url: request.url() });
    const url = new URL(request.url());
    if (url.pathname === `${prefix}/balances`) {
      return route.fulfill({ json: sampleBalance });
    }
    if (url.pathname === `${prefix}/orders` && url.searchParams.get('limit') === '50') {
      return route.fulfill({ json: sampleOrders });
    }
    if (url.pathname === `${prefix}/evaluation`) {
      return route.fulfill({ json: sampleEvaluation });
    }
    return route.fulfill({ status: 404, json: { error: 'Unexpected mock API path' } });
  });

  await page.goto('/');
  await page.getByTitle('Paper Portfolio').click();
  await page.getByLabel('Paper wallet ID (UUID)').fill('not-a-uuid');
  await page.getByRole('button', { name: 'Load wallet' }).click();
  await expect(page.getByRole('alert')).toContainText('valid paper wallet UUID');
  expect(requests).toHaveLength(0);

  await page.getByLabel('Paper wallet ID (UUID)').fill(walletId);
  await page.getByRole('button', { name: 'Load wallet' }).click();
  await expect(page.getByText('100', { exact: true })).toBeVisible();
  await expect(page.getByRole('row', { name: /BTCUSDT BUY 0.01 NEW/ })).toBeVisible();
  await expect(page.getByText('Recorded fills')).toBeVisible();
  await expect(page.getByText('Returns and drawdown unavailable', { exact: false })).toBeVisible();
  await expect(page.getByText('cash movement is not profit', { exact: false })).toBeVisible();
  await expect.poll(() => requests.length).toBe(3);

  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect.poll(() => requests.length).toBe(6);
  expect(requests.every((item) => item.method === 'GET')).toBe(true);
  expect(requests.every((item) => new URL(item.url).hostname === '127.0.0.1')).toBe(true);

  await page.reload();
  await page.getByTitle('Paper Portfolio').click();
  await expect(page.getByText('100', { exact: true })).toBeVisible();
  // React dev-mode effects may issue additional GETs after a remount.\n  // Require the post-reload batch but do not couple acceptance to exact count.\n  await expect.poll(() => requests.length).toBeGreaterThanOrEqual(9);
  expect(requests.every((item) => item.method === 'GET')).toBe(true);
  expect(pageErrors).toEqual([]);
});

test('unauthorized wallet reads display error without showing stale balances or sending orders', async ({ page }) => {
  const methods = [];
  await page.route('**/api/v1/paper/**', async (route) => {
    methods.push(route.request().method());
    return route.fulfill({ status: 401, json: { message: 'Unauthorized' } });
  });
  await page.goto('/');
  await page.getByTitle('Paper Portfolio').click();
  await page.getByLabel('Paper wallet ID (UUID)').fill(walletId);
  await page.getByRole('button', { name: 'Load wallet' }).click();
  await expect(page.getByRole('alert')).toContainText('Portfolio API unavailable');
  await expect(page.getByText('No balances loaded.')).toBeVisible();
  expect(methods).toEqual(['GET', 'GET', 'GET']);
});
