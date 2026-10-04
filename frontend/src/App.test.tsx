import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import App from './App';
import { api, ApiError, type Duck } from './api';

const duck: Duck = { id: 1, color: 'Red', size: 'Large', price: 10, quantity: 20, version: 0, deleted: false };

describe('warehouse', () => {
  beforeEach(() => { vi.restoreAllMocks(); vi.spyOn(api, 'list').mockResolvedValue([duck]); });

  it('shows inventory and requires confirmation before deletion', async () => {
    const remove = vi.spyOn(api, 'remove').mockResolvedValue(undefined);
    render(<App />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Delete duck 1' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete duck 1' }));
    vi.mocked(api.list).mockResolvedValue([]);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete duck' }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(1));
    expect(await screen.findByText('Your warehouse is ready.')).toBeVisible();
  });

  it('shows merge feedback and refreshed stock', async () => {
    const merged = { ...duck, quantity: 25, version: 1 };
    vi.spyOn(api, 'add').mockResolvedValue({ outcome: 'merged', duck: merged });
    render(<App />);
    const user = userEvent.setup();
    await screen.findByRole('button', { name: 'Edit duck 1' });
    await user.click(screen.getByRole('button', { name: /Add duck/ }));
    await user.type(screen.getByLabelText(/Unit price/), '10');
    await user.type(screen.getByLabelText('Quantity'), '5');
    vi.mocked(api.list).mockResolvedValue([merged]);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Add duck' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Stock combined into duck #1.');
    expect(await within(screen.getByRole('table')).findByText('25')).toBeVisible();
  });

  it('can retry an inventory load failure', async () => {
    vi.mocked(api.list).mockRejectedValueOnce(new ApiError('Database unavailable.', 'DATABASE_UNAVAILABLE'));
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable.');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('button', { name: 'Edit duck 1' })).toBeVisible();
  });
});
