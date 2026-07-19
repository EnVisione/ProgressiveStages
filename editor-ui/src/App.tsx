import { NAVIGATION } from "./data";
import { DialogHost, ErrorScreen, LoadingScreen, NoticeCenter, Button, Badge } from "./components/ui";
import { Icon } from "./components/Icon";
import { ReviewDrawer } from "./components/ReviewDrawer";
import { ExtensionsPage } from "./features/ExtensionsPage";
import { LayoutPage } from "./features/LayoutPage";
import { OverviewPage } from "./features/OverviewPage";
import { RegistryPage } from "./features/RegistryPage";
import { SettingsPage } from "./features/SettingsPage";
import { StageSidebar } from "./features/stages/StageSidebar";
import { StageWorkspace } from "./features/stages/StageWorkspace";
import { useEditor } from "./store/EditorContext";
import type { PageId } from "./types";

function CurrentPage() {
  const { page } = useEditor();
  if (page === "overview") return <OverviewPage/>;
  if (page === "stages") return <StageWorkspace/>;
  if (page === "layout") return <LayoutPage/>;
  if (page === "settings") return <SettingsPage/>;
  if (page === "registry") return <RegistryPage/>;
  return <ExtensionsPage/>;
}

export function App() {
  const { boot, busy, error, page, setPage, undo, redo, validate, openReview, refresh } = useEditor();
  if (error) return <ErrorScreen message={error} retry={() => void refresh()}/>;
  if (!boot) return <LoadingScreen message={busy || "Connecting to Minecraft"}/>;
  return <div className={`editor-app page-${page}`}>
    <header className="app-header"><button className="brand-button" onClick={() => setPage("overview")}><img src="/logo.png" alt=""/><span><strong>ProgressiveStages</strong><small>Stage studio</small></span></button><div className="server-status"><span className="status-dot"/><strong>Connected</strong><span>Draft {boot.draft.revision}</span><span>Server {boot.catalog.configurationRevision}</span>{busy ? <Badge tone="warning">{busy}</Badge> : null}</div><div className="header-actions"><Button tone="quiet" icon="undo" disabled={!boot.draft.canUndo || Boolean(busy)} onClick={() => void undo()}>Undo</Button><Button tone="quiet" icon="redo" disabled={!boot.draft.canRedo || Boolean(busy)} onClick={() => void redo()}>Redo</Button><Button disabled={Boolean(busy)} onClick={() => void validate()}>Check my work</Button><Button tone="primary" icon="check" disabled={Boolean(busy)} onClick={() => void openReview()}>Review and apply</Button></div></header>
    <div className={`app-body ${page === "stages" ? "with-stage-library" : ""}`}><aside className="primary-nav"><nav>{NAVIGATION.map(item => <button key={item.id} className={page === item.id ? "active" : ""} onClick={() => setPage(item.id as PageId)}><span><Icon name={item.icon}/></span><span><strong>{item.label}</strong><small>{item.detail}</small></span>{page === item.id ? <i/> : null}</button>)}</nav><footer><div className="nav-help"><Icon name="spark"/><div><strong>Need exact control</strong><p>Every stage keeps a TOML source tab.</p></div></div><span>Local editor. Operator only.</span></footer></aside>{page === "stages" ? <StageSidebar/> : null}<main className="page-content"><CurrentPage/></main></div>
    <DialogHost/><ReviewDrawer/><NoticeCenter/>
  </div>;
}
