import { useEffect, useId, useRef } from "react";
import { useEditor } from "../store/EditorContext";
import { Icon } from "./Icon";

export function Button({ children, tone = "neutral", icon, className = "", ...props }:
  React.ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "neutral" | "primary" | "danger" | "quiet"; icon?: string }) {
  return <button {...props} className={`button button-${tone} ${className}`.trim()}>
    {icon ? <Icon name={icon} size={17}/> : null}<span>{children}</span>
  </button>;
}

export function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: string }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Field({ label, help, wide, children }: { label: string; help?: string; wide?: boolean; children: React.ReactNode }) {
  return <label className={`field ${wide ? "field-wide" : ""}`}>
    <span className="field-label">{label}</span>
    {children}
    {help ? <span className="field-help">{help}</span> : null}
  </label>;
}

export function Toggle({ label, help, checked, onChange, disabled }:
  { label: string; help?: string; checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean }) {
  return <label className="toggle-row">
    <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} disabled={disabled}/>
    <span className="toggle-control" aria-hidden="true"><span/></span>
    <span><strong>{label}</strong>{help ? <small>{help}</small> : null}</span>
  </label>;
}

export function Section({ title, description, action, children, className = "" }:
  { title: string; description?: string; action?: React.ReactNode; children: React.ReactNode; className?: string }) {
  return <section className={`section-card ${className}`}>
    <header className="section-header"><div><h2>{title}</h2>{description ? <p>{description}</p> : null}</div>{action}</header>
    <div className="section-content">{children}</div>
  </section>;
}

export function EmptyState({ icon = "spark", title, description, action }:
  { icon?: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="empty-state"><span className="empty-icon"><Icon name={icon} size={24}/></span><div><strong>{title}</strong><p>{description}</p></div>{action}</div>;
}

export function DialogHost() {
  const { dialog, closeDialog } = useEditor();
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useEffect(() => {
    if (!dialog) return;
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") closeDialog(); };
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("keydown", escape); previous?.focus(); };
  }, [dialog, closeDialog]);
  if (!dialog) return null;
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) closeDialog(); }}>
    <div ref={panel} tabIndex={-1} className={`dialog dialog-${dialog.width || "standard"}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <header className="dialog-header"><div><h2 id={titleId}>{dialog.title}</h2>{dialog.description ? <p>{dialog.description}</p> : null}</div><Button tone="quiet" icon="close" aria-label="Close dialog" onClick={closeDialog}/></header>
      <div className="dialog-body">{dialog.content}</div>
    </div>
  </div>;
}

export function NoticeCenter() {
  const { notices, dismissNotice } = useEditor();
  return <div className="notice-center" aria-live="polite">{notices.map(notice =>
    <article key={notice.id} className={`notice notice-${notice.tone}`}>
      <span className="notice-mark"><Icon name={notice.tone === "danger" ? "warning" : "check"} size={17}/></span>
      <div><strong>{notice.title}</strong>{notice.message ? <p>{notice.message}</p> : null}</div>
      <button aria-label="Dismiss" onClick={() => dismissNotice(notice.id)}><Icon name="close" size={15}/></button>
    </article>)}
  </div>;
}

export function LoadingScreen({ message }: { message: string }) {
  return <main className="loading-screen"><img src="/logo.png" alt="ProgressiveStages lock logo"/><div className="loading-ring"/><h1>ProgressiveStages</h1><p>{message}</p></main>;
}

export function ErrorScreen({ message, retry }: { message: string; retry: () => void }) {
  return <main className="loading-screen error-screen"><img src="/logo.png" alt="ProgressiveStages lock logo"/><Badge tone="danger">Connection problem</Badge><h1>The editor could not start</h1><p>{message}</p><Button tone="primary" onClick={retry}>Try again</Button></main>;
}
