import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ApiError, type Duck } from '../api';
import DuckForm from './DuckForm';

const duck: Duck = { id: 1, color: 'Red', size: 'Large', price: 10, quantity: 20, version: 2, deleted: false };

describe('DuckForm', () => {
  it('uses the add form to submit color, size, exact price text, and whole quantity', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    render(<DuckForm duck={null} onSave={save} onClose={vi.fn()} onReload={vi.fn()} />);
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText('Color'), 'Green');
    await user.selectOptions(screen.getByLabelText('Size'), 'Medium');
    await user.type(screen.getByLabelText(/Unit price/), '7.25');
    await user.type(screen.getByLabelText('Quantity'), '12');
    await user.click(screen.getByRole('button', { name: 'Add duck' }));
    expect(save).toHaveBeenCalledWith({ color: 'Green', size: 'Medium', price: '7.25', quantity: 12 });
  });

  it('keeps color and size read-only during edits', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    render(<DuckForm duck={duck} onSave={save} onClose={vi.fn()} onReload={vi.fn()} />);
    expect(screen.getByLabelText('Color')).toBeDisabled();
    expect(screen.getByLabelText('Size')).toBeDisabled();
    const user = userEvent.setup();
    await user.clear(screen.getByLabelText('Quantity'));
    await user.type(screen.getByLabelText('Quantity'), '0');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(save).toHaveBeenCalledWith({ color: 'Red', size: 'Large', price: '10.00', quantity: 0 });
  });

  it('rejects fractional quantities before calling the API', async () => {
    const save = vi.fn();
    render(<DuckForm duck={duck} onSave={save} onClose={vi.fn()} onReload={vi.fn()} />);
    const user = userEvent.setup();
    await user.clear(screen.getByLabelText('Quantity'));
    await user.type(screen.getByLabelText('Quantity'), '1.5');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(screen.getByRole('alert')).toHaveTextContent('whole quantity');
    expect(save).not.toHaveBeenCalled();
  });

  it('offers reload when the backend rejects a stale edit', async () => {
    const reload = vi.fn().mockResolvedValue(undefined);
    render(<DuckForm duck={duck} onSave={vi.fn().mockRejectedValue(new ApiError('Inventory changed.', 'STALE_VERSION'))}
      onClose={vi.fn()} onReload={reload} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Inventory changed.');
    await user.click(screen.getByRole('button', { name: 'Reload inventory' }));
    expect(reload).toHaveBeenCalledOnce();
  });

  it('prevents resubmission after an ambiguous commit until inventory is reloaded', async () => {
    render(<DuckForm duck={duck} onSave={vi.fn().mockRejectedValue(new ApiError('Reload before retrying.', 'COMMIT_OUTCOME_UNKNOWN'))}
      onClose={vi.fn()} onReload={vi.fn()} />);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByRole('button', { name: 'Reload inventory' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
  });

  it('allows saving after reload even when the record version did not change', async () => {
    const save = vi.fn().mockRejectedValueOnce(new ApiError('Reload before retrying.', 'COMMIT_OUTCOME_UNKNOWN'))
      .mockResolvedValue(undefined);
    render(<DuckForm duck={duck} onSave={save} onClose={vi.fn()} onReload={vi.fn().mockResolvedValue(undefined)} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    await user.click(await screen.findByRole('button', { name: 'Reload inventory' }));
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(save).toHaveBeenCalledTimes(2);
  });
});
