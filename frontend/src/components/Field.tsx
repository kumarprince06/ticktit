import type { InputHTMLAttributes } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string };

/** Labelled input with the field error from the API (ProblemDetail "errors") shown under it. */
export function Field({ label, error, hint, id, ...input }: Props) {
  const inputId = id ?? input.name;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;
  return (
    <div className={`field${error ? " field--error" : ""}`}>
      <label htmlFor={inputId}>{label}</label>
      <input id={inputId} aria-invalid={!!error} aria-describedby={describedBy} {...input} />
      {error ? (
        <p id={`${inputId}-error`} className="field__error">{error}</p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="field__hint">{hint}</p>
      ) : null}
    </div>
  );
}

/** A banner for errors that are not about one field (wrong password, too many attempts, network...). */
export function FormError({ message }: { message?: string | null }) {
  if (!message) return null;
  return <div className="form-error" role="alert">{message}</div>;
}

export function FormNotice({ message }: { message?: string | null }) {
  if (!message) return null;
  return <div className="form-notice" role="status">{message}</div>;
}
