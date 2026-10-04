import { useState, type FormEvent } from "react";
import {
  ApiError,
  colors,
  sizes,
  type Color,
  type Size,
  type Duck,
  type DuckFields,
} from "../api";
import Dialog from "./Dialog";

export default function DuckForm({
  duck,
  onSave,
  onClose,
  onReload,
}: {
  duck: Duck | null;
  onSave: (fields: DuckFields) => Promise<void>;
  onClose: () => void;
  onReload: () => Promise<void>;
}) {
  const [color, setColor] = useState<Color>(duck?.color ?? "Yellow");
  const [size, setSize] = useState<Size>(duck?.size ?? "Small");
  const [price, setPrice] = useState(duck?.price.toFixed(2) ?? "");
  const [quantity, setQuantity] = useState(duck?.quantity.toString() ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (
      !/^(?:0|[1-9]\d{0,8})(?:\.\d{1,2})?$/.test(price) ||
      Number(price) <= 0
    ) {
      setError(
        new ApiError(
          "Enter a positive USD price with at most two decimal places.",
          "FORM_ERROR",
        ),
      );
      return;
    }
    if (!/^\d+$/.test(quantity) || Number(quantity) > 2147483647) {
      setError(
        new ApiError(
          "Enter a whole quantity from 0 to 2,147,483,647.",
          "FORM_ERROR",
        ),
      );
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSave({ color, size, price, quantity: Number(quantity) });
    } catch (problem) {
      setError(
        problem instanceof ApiError
          ? problem
          : new ApiError("Could not save this duck.", "REQUEST_FAILED"),
      );
    } finally {
      setBusy(false);
    }
  }

  async function reload() {
    setBusy(true);
    try {
      await onReload();
      setError(null);
    } catch {
      setError(
        new ApiError("Could not reload inventory. Try again.", "NETWORK_ERROR"),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={duck ? `Edit duck #${duck.id}` : "Add a duck"}
      onClose={onClose}
      busy={busy}
    >
      <form onSubmit={submit}>
        <p className="form-intro">
          {duck
            ? "Update the price or stock quantity."
            : "A matching color, size, and price will combine with existing stock."}
        </p>
        <div className="form-grid">
          <label>
            Color
            <select
              value={color}
              onChange={(e) => setColor(e.target.value as Color)}
              disabled={!!duck || busy}
            >
              {colors.map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
          </label>
          <label>
            Size
            <select
              value={size}
              onChange={(e) => setSize(e.target.value as Size)}
              disabled={!!duck || busy}
            >
              {sizes.map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
          </label>
          <label>
            Unit price <span className="label-hint">USD</span>
            <input
              inputMode="decimal"
              required
              placeholder="0.00"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              disabled={busy}
            />
          </label>
          <label>
            Quantity
            <input
              inputMode="numeric"
              required
              placeholder="0"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              disabled={busy}
            />
          </label>
        </div>
        {duck && (
          <p className="form-note">
            If this price matches another duck, their quantities will be
            combined using the quantity entered here.
          </p>
        )}
        {error && (
          <div className="form-error" role="alert">
            <p>{error.message}</p>
            {error.fields && (
              <ul>
                {Object.entries(error.fields).map(([field, message]) => (
                  <li key={field}>
                    {field}: {message}
                  </li>
                ))}
              </ul>
            )}
            {(error.code === "STALE_VERSION" ||
              error.code === "COMMIT_OUTCOME_UNKNOWN") && (
              <button
                type="button"
                className="text-button"
                onClick={reload}
                disabled={busy}
              >
                Reload inventory
              </button>
            )}
          </div>
        )}
        <footer className="dialog-actions">
          <button
            className="secondary-button"
            type="button"
            onClick={onClose}
            disabled={busy}
          >
            Cancel
          </button>
          <button
            className="primary-button"
            type="submit"
            disabled={busy || error?.code === "COMMIT_OUTCOME_UNKNOWN"}
          >
            {busy ? "Saving…" : duck ? "Save changes" : "Add duck"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
