import { useEffect, useState } from 'react';
import { api, usd, type Duck, type DuckFields } from './api';
import DuckForm from './components/DuckForm';
import DeleteDialog from './components/DeleteDialog';

function DuckMark() {
  return <svg viewBox="0 0 48 48" fill="none" aria-hidden="true"><path d="M8 26c0 11 6 16 18 16 11 0 16-6 16-14H29c3-2 5-5 5-10 0-7-5-12-12-12S10 11 10 18c0 3 1 6 3 8H8Z" fill="#FFD65A"/><path d="m31 16 11 4-11 4" fill="#EE9B38"/><circle cx="25" cy="16" r="2" fill="#233A30"/><path d="M17 30c4 5 10 6 15 2" stroke="#DCAE35" strokeWidth="2" strokeLinecap="round"/></svg>;
}

export default function App() {
  const [ducks, setDucks] = useState<Duck[] | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editor, setEditor] = useState<Duck | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<Duck | null>(null);
  const [reloading, setReloading] = useState(false);

  async function refresh() {
    setReloading(true);
    try { const result = await api.list(); setDucks(result); setError(''); return result; }
    catch (problem) { setError(problem instanceof Error ? problem.message : 'Could not load inventory.'); throw problem; }
    finally { setReloading(false); }
  }
  useEffect(() => { void refresh().catch(() => {}); }, []);

  async function save(fields: DuckFields) {
    const result = editor ? await api.edit(editor, fields) : await api.add(fields);
    setEditor(undefined);
    setNotice(result.outcome === 'merged' ? `Stock combined into duck #${result.duck.id}.` : `Duck #${result.duck.id} ${result.outcome}.`);
    await refresh().catch(() => {});
  }
  async function reloadEditor() {
    const current = await refresh();
    if (!editor) { setEditor(undefined); return; }
    const replacement = current.find(duck => duck.id === editor.id);
    if (replacement) setEditor(replacement);
    else { setEditor(undefined); setNotice('This duck is no longer active. Inventory has been refreshed.'); }
  }
  async function remove() {
    if (!deleting) return;
    await api.remove(deleting.id);
    setNotice(`Duck #${deleting.id} removed from active inventory.`);
    setDeleting(null);
    await refresh().catch(() => {});
  }

  const totalUnits = ducks?.reduce((sum, duck) => sum + duck.quantity, 0) ?? 0;
  const inventoryValue = ducks?.reduce((sum, duck) => sum + duck.price * duck.quantity, 0) ?? 0;

  return <>
    <nav className="topbar" aria-label="Main"><div className="topbar-inner">
      <div className="brand"><span className="brand-mark"><DuckMark /></span><span>duck<span className="brand-light">warehouse</span></span></div>
      <span className="nav-label"><span className="status-dot" /> INVENTORY MANAGEMENT</span>
    </div></nav>
    <main>
      <header className="page-header"><div><p className="eyebrow">A PLACE FOR EVERY DUCK</p>
        <h1>Duck Warehouse<span className="title-dot">.</span></h1>
        <p className="page-description">Keep your flock organized. Manage prices and stock in one place.</p>
      </div><button className="primary-button add-button" onClick={() => { setNotice(''); setEditor(null); }}><span aria-hidden="true">＋</span> Add duck</button></header>
      <section className="metrics" aria-label="Inventory overview">
        <div className="metric"><span>Active records</span><strong>{ducks ? ducks.length.toLocaleString() : '—'}</strong><small>Color, size & price combinations</small></div>
        <div className="metric"><span>Total ducks</span><strong>{ducks ? totalUnits.toLocaleString() : '—'}</strong><small>Units in your warehouse</small></div>
        <div className="metric"><span>Inventory value</span><strong>{ducks ? usd.format(inventoryValue) : '—'}</strong><small>At current unit prices · USD</small></div>
      </section>
      {notice && <div className="notice" role="status"><span>✓ {notice}</span><button className="icon-button" aria-label="Dismiss notification" onClick={() => setNotice('')}>×</button></div>}
      {error && <div className="error-banner" role="alert"><span>{error}</span><button className="text-button" disabled={reloading} onClick={() => void refresh().catch(() => {})}>Retry</button></div>}
      <section className="inventory-panel" aria-labelledby="inventory-title">
        <div className="panel-header"><div><h2 id="inventory-title">Your inventory <span className="count-badge">{ducks?.length ?? 0}</span></h2><p>All active ducks, ready for their next adventure.</p></div><span className="sort-label">↗ Quantity, low to high</span></div>
        <div className="table-scroll"><table><thead><tr><th scope="col">ID</th><th scope="col">Color</th><th scope="col">Size</th><th scope="col" className="numeric">Unit price</th><th scope="col" className="numeric">Quantity</th><th scope="col" className="actions-heading">Actions</th></tr></thead>
          <tbody>{ducks?.map(duck => <tr key={duck.id}>
            <td className="id-cell">#{String(duck.id).padStart(3, '0')}</td>
            <td><span className={`color-chip color-${duck.color.toLowerCase()}`}><span className="color-dot" />{duck.color}</span></td>
            <td><span className="size-label">{duck.size}</span></td>
            <td className="numeric price-cell">{usd.format(duck.price)}<span className="currency-label"> USD</span></td>
            <td className="numeric"><span className={`quantity ${duck.quantity === 0 ? 'out-of-stock' : ''}`}>{duck.quantity.toLocaleString()}</span></td>
            <td className="row-actions"><button className="edit-button" aria-label={`Edit duck ${duck.id}`} onClick={() => setEditor(duck)}>Edit</button>
              <button className="delete-button" aria-label={`Delete duck ${duck.id}`} onClick={() => setDeleting(duck)}>Delete</button></td>
          </tr>)}</tbody></table></div>
        {ducks === null && !error && <div className="empty-state" role="status"><span className="loading-dot" /> Loading your flock…</div>}
        {ducks?.length === 0 && <div className="empty-state"><span className="empty-duck"><DuckMark /></span><h3>Your warehouse is ready.</h3><p>Add your first duck to start building your flock.</p><button className="secondary-button" onClick={() => setEditor(null)}>Add your first duck</button></div>}
        <footer className="panel-footer"><span>{ducks?.length ?? 0} active records</span><span>Prices in US dollars</span></footer>
      </section>
      <footer className="page-footer"><span>Small ducks. Well-kept stock.</span><span>Duck Warehouse</span></footer>
    </main>
    {editor !== undefined && <DuckForm key={editor ? `${editor.id}:${editor.version}` : 'new'} duck={editor} onSave={save} onClose={() => setEditor(undefined)} onReload={reloadEditor} />}
    {deleting && <DeleteDialog duck={deleting} onDelete={remove} onClose={() => setDeleting(null)} />}
  </>;
}
