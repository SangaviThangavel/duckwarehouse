import { useState } from 'react';
import { ApiError, type Duck } from '../api';
import Dialog from './Dialog';

export default function DeleteDialog({ duck, onDelete, onClose }: {
  duck: Duck; onDelete: () => Promise<void>; onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function confirm() {
    if (busy) return;
    setBusy(true); setError('');
    try { await onDelete(); }
    catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Could not delete this duck.'); }
    finally { setBusy(false); }
  }
  return <Dialog title={`Delete duck #${duck.id}?`} onClose={onClose} busy={busy}>
    <p className="delete-copy">Remove this {duck.color.toLowerCase()} {duck.size} duck from active inventory? Its record will be kept in the warehouse history.</p>
    {error && <p role="alert" className="form-error">{error}</p>}
    <footer className="dialog-actions"><button className="secondary-button" onClick={onClose} disabled={busy}>Cancel</button>
      <button className="danger-button" onClick={confirm} disabled={busy}>{busy ? 'Deleting…' : 'Delete duck'}</button>
    </footer>
  </Dialog>;
}
