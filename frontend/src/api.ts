export const colors = ["Red", "Green", "Yellow", "Black"] as const;
export const sizes = ["XLarge", "Large", "Medium", "Small", "XSmall"] as const;
export type Color = (typeof colors)[number];
export type Size = (typeof sizes)[number];
export interface Duck {
  id: number;
  color: Color;
  size: Size;
  price: number;
  quantity: number;
  deleted: boolean;
  version: number;
}
export interface DuckFields {
  color: Color;
  size: Size;
  price: string;
  quantity: number;
}
export interface Mutation {
  duck: Duck;
  outcome: "created" | "updated" | "merged";
}

export class ApiError extends Error {
  constructor(
    message: string,
    public code: string,
    public fields?: Record<string, string>,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError(
      "Could not reach the warehouse. Check your connection and try again.",
      "NETWORK_ERROR",
    );
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(
      problem.detail ?? "The request could not be completed.",
      problem.code ?? "REQUEST_FAILED",
      problem.errors,
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export const api = {
  list: () => request<Duck[]>("/ducks"),
  add: (fields: DuckFields) =>
    request<Mutation>("/ducks", {
      method: "POST",
      body: JSON.stringify(fields),
    }),
  edit: (duck: Duck, fields: DuckFields) =>
    request<Mutation>(`/ducks/${duck.id}`, {
      method: "PUT",
      body: JSON.stringify({
        price: fields.price,
        quantity: fields.quantity,
        version: duck.version,
      }),
    }),
  remove: (id: number) => request<void>(`/ducks/${id}`, { method: "DELETE" }),
};
export const usd = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});
