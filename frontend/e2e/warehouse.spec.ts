import { test, expect } from '@playwright/test';

test('warehouse create, merge, edit, quote, and logical delete', async ({ page, request }) => {
  const price = (10000 + (Date.now() % 100000000) / 100).toFixed(2);
  const owned = new Set<number>();
  try {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Duck Warehouse.' })).toBeVisible();
    await page.getByRole('button', { name: 'Add duck', exact: true }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByLabel('Color').selectOption('Black');
    await dialog.getByLabel('Size').selectOption('XSmall');
    await dialog.getByLabel('Unit price').fill(price);
    await dialog.getByLabel('Quantity').fill('17');
    const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/ducks') && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: 'Add duck', exact: true }).click();
    const created = await (await createdResponse).json();
    owned.add(created.duck.id);
    let row = page.getByRole('row').filter({ has: page.getByRole('button', { name: `Edit duck ${created.duck.id}`, exact: true }) });
    await expect(row).toContainText('17');

    const merge = await request.post('http://127.0.0.1:8081/api/ducks', { data: { color: 'Black', size: 'XSmall', price, quantity: 3 } });
    expect(merge.ok()).toBeTruthy();
    expect((await merge.json()).outcome).toBe('merged');
    await page.reload();
    await expect(row).toContainText('20');
    await row.getByRole('button', { name: `Edit duck ${created.duck.id}`, exact: true }).click();
    dialog = page.getByRole('dialog');
    await expect(dialog.getByLabel('Color')).toBeDisabled();
    await expect(dialog.getByLabel('Size')).toBeDisabled();
    await dialog.getByLabel('Quantity').fill('25');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(row).toContainText('25');

    const quote = await request.post('http://127.0.0.1:8081/api/orders/quote', { data: {
      color: 'Black', size: 'XSmall', quantity: 1, destinationCountry: 'India', shippingMode: 'Sea',
    } });
    expect(quote.ok()).toBeTruthy();
    const quoted = await quote.json();
    expect(quoted.packageType).toBe('Plastic');
    expect(quoted.protectionTypes).toEqual(['MoistureAbsorbingBeads', 'BubbleWrapBags']);
    expect(quoted.breakdown.reduce((total: number, entry: { amount: number }) => total + Math.round(entry.amount * 100), 0))
      .toBe(Math.round(quoted.total * 100));

    if (process.env.WAREHOUSE_SCREENSHOT) await page.screenshot({ path: process.env.WAREHOUSE_SCREENSHOT, fullPage: true });
    await row.getByRole('button', { name: `Delete duck ${created.duck.id}`, exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: `Delete duck ${created.duck.id}`, exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete duck', exact: true }).click();
    await expect(row).toHaveCount(0);
    const listing = await request.get('http://127.0.0.1:8081/api/ducks');
    expect((await listing.json()).some((duck: { id: number }) => duck.id === created.duck.id)).toBeFalsy();

    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole('button', { name: 'Add duck', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  } finally {
    for (const id of owned) await request.delete(`http://127.0.0.1:8081/api/ducks/${id}`);
  }
});
