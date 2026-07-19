import { NAVIGATION } from "./data";
import { DialogHost, ErrorScreen, LoadingScreen, NoticeCenter, Button } from "./components/ui";
import { Icon } from "./components/Icon";
import { ReviewDrawer } from "./components/ReviewDrawer";
import { ExtensionsPage } from "./features/ExtensionsPage";
import { LayoutPage } from "./features/LayoutPage";
import { RegistryPage } from "./features/RegistryPage";
import { SettingsPage } from "./features/SettingsPage";
import { StageSidebar } from "./features/stages/StageSidebar";
import { StageWorkspace } from "./features/stages/StageWorkspace";
import { useEditor } from "./store/EditorContext";
import type { PageId } from "./types";

function CurrentPage() {
  const { page } = useEditor();
  if (page === "stages") return <StageWorkspace/>;
  if (page === "layout") return <LayoutPage/>;
  if (page === "settings") return <SettingsPage/>;
  if (page === "registry") return <RegistryPage/>;
  return <ExtensionsPage/>;
}

export function App() {
  const { boot, busy, error, page, setPage, undo, redo, openReview, refresh } = useEditor();
  if (error) return <ErrorScreen message={error} retry={() => void refresh()}/>;
  if (!boot) return <LoadingScreen message={busy || "Connecting to Minecraft"}/>;
  return <div className={`editor-app page-${page}`}>
    <header className="app-header">
      <button className="brand-button" onClick={() => setPage("stages")}><img src="/logo.png" alt=""/><strong>ProgressiveStages</strong></button>
      <nav className="top-navigation" aria-label="Editor pages">{NAVIGATION.map(item => <button key={item.id} className={page === item.id ? "active" : ""} onClick={() => setPage(item.id as PageId)}><Icon name={item.icon} size={16}/><span>{item.label}</span></button>)}</nav>
      <div className="header-actions">
        <span className="save-state"><i/>{busy || (boot.draft.diff.length ? `${boot.draft.diff.length} change${boot.draft.diff.length === 1 ? "" : "s"}` : "Saved")}</span>
        <Button tone="quiet" icon="undo" disabled={!boot.draft.canUndo || Boolean(busy)} onClick={() => void undo()}>Undo</Button>
        <Button tone="quiet" icon="redo" disabled={!boot.draft.canRedo || Boolean(busy)} onClick={() => void redo()}>Redo</Button>
        <Button tone="primary" disabled={Boolean(busy)} onClick={() => void openReview()}>Apply changes</Button>
      </div>
    </header>
    <div className={`app-body ${page === "stages" ? "with-stage-library" : ""}`}>{page === "stages" ? <StageSidebar/> : null}<main className="page-content"><CurrentPage/></main></div>
    <DialogHost/><ReviewDrawer/><NoticeCenter/>
  </div>;
}
