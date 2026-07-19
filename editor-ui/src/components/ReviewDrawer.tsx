import { Badge, Button, EmptyState } from "./ui";
import { Icon } from "./Icon";
import { useEditor } from "../store/EditorContext";

export function ReviewDrawer() {
  const { review, applyResult, closeReview, apply, rollback } = useEditor();
  if (!review) return null;
  return <div className="review-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) closeReview(); }}><section className="review-drawer" role="dialog" aria-modal="true" aria-label="Apply stage changes">
    <header><div><h2>Apply changes</h2><p>Review the validated file changes before they reach the server.</p></div><Button tone="quiet" icon="close" aria-label="Close review" onClick={closeReview}/></header>
    <div className={`validation-banner ${review.validation.valid ? "valid" : "invalid"}`}><Icon name={review.validation.valid ? "check" : "warning"} size={25}/><div><strong>{review.validation.valid ? `${review.validation.stages} stages compiled successfully` : `${review.validation.errors.length} validation problems found`}</strong><p>{review.validation.valid ? "The draft is safe to apply to the authoritative server." : "Correct every error before applying this draft."}</p></div><Badge tone={review.validation.valid ? "success" : "danger"}>{review.validation.valid ? "Valid" : "Blocked"}</Badge></div>
    {review.validation.errors.length ? <div className="review-messages"><h3>Errors</h3>{review.validation.errors.map((message, index) => <article key={index} className="error"><Icon name="warning" size={16}/><span>{message}</span></article>)}</div> : null}
    {review.validation.warnings.length ? <div className="review-messages"><h3>Warnings</h3>{review.validation.warnings.map((message, index) => <article key={index}><Icon name="warning" size={16}/><span>{message}</span></article>)}</div> : null}
    <div className="review-changes"><h3>Stage file changes</h3><p>Added files are green, modified files are yellow, and removed files are red. Operators receive the same color coded summary in Minecraft chat after a successful apply.</p>{review.diff.length ? review.diff.map(entry => <article key={entry.path}><Badge tone={entry.change === "ADDED" ? "success" : entry.change === "DELETED" ? "danger" : "warning"}>{entry.change}</Badge><code>{entry.path}</code><span>{entry.beforeBytes} to {entry.afterBytes} bytes</span></article>) : <EmptyState icon="check" title="No pending file changes" description="The editor draft already matches the live server."/>}</div>
    {applyResult ? <div className="apply-result"><Icon name="check"/><div><strong>The live server is synchronized</strong><p>{applyResult.explanation || `Configuration revision ${applyResult.configurationRevision}.`}</p><code>{applyResult.transactionId}</code></div></div> : null}
    <footer>{applyResult?.transactionId ? <Button tone="danger" onClick={() => void rollback(applyResult.transactionId)}>Roll back this transaction</Button> : <span/>}<div><Button tone="quiet" onClick={closeReview}>Close</Button><Button tone="primary" disabled={!review.validation.valid || !review.diff.length || Boolean(applyResult)} onClick={() => void apply()}>{applyResult ? "Applied to server" : "Confirm and apply to server"}</Button></div></footer>
  </section></div>;
}
