import { render } from "preact";
import "../../src/main/resources/assets/progressivestages/editor/app.css";
import "./editor.css";

function App() {
  return <>
    <header class="topbar">
      <div class="brand"><span class="lock">◆</span><div><strong>ProgressiveStages</strong><small>Server editor</small></div></div>
      <div class="status"><span id="connection" class="pill pending">Connecting</span><span id="revision" class="muted"></span></div>
      <div class="actions"><button id="undo" class="ghost">Undo</button><button id="redo" class="ghost">Redo</button><button id="validate">Validate</button><button id="review" class="primary">Review and apply</button></div>
    </header>
    <main class="workspace">
      <aside class="navigator panel">
        <div class="panel-title"><span>Stages and files</span><button id="newStage" aria-label="Create stage">＋</button></div>
        <input id="fileSearch" type="search" placeholder="Search stages" aria-label="Search stages" />
        <nav id="files" class="file-list"></nav>
        <div class="stage-actions">
          <button id="duplicateStage" class="ghost">Duplicate</button>
          <button id="renameStage" class="ghost">Rename</button>
          <button id="moveStage" class="ghost">Move</button>
          <button id="archiveStage" class="ghost">Archive or restore</button>
          <button id="exportStage" class="ghost">Export</button>
          <button id="importStage" class="ghost">Import</button>
          <button id="deleteStage" class="ghost danger">Delete</button>
        </div>
        <button id="settings" class="wide ghost">Main settings</button>
      </aside>
      <section class="canvas panel">
        <div class="tabs"><button data-view="form" class="active">Visual form</button><button data-view="source">TOML source</button><button data-view="graph">Stage graph</button></div>
        <div id="breadcrumb" class="breadcrumb"></div>
        <div id="formView" class="view"></div>
        <div id="sourceView" class="view hidden"><textarea id="source" spellcheck={false} aria-label="TOML source"></textarea><div class="source-actions"><span id="dirty" class="muted"></span><button id="saveSource" class="primary">Save draft</button></div></div>
        <div id="graphView" class="view hidden"><div id="graph" class="graph"></div></div>
      </section>
      <aside class="inspector panel">
        <div class="panel-title"><span>Inspector</span><button id="theme" class="ghost" aria-label="Toggle theme">◐</button></div>
        <div class="inspector-tabs"><button data-inspector="help" class="active">Help</button><button data-inspector="catalog">Registry</button><button data-inspector="extensions">Extensions</button><button data-inspector="conflicts">Conflicts</button></div>
        <div id="helpPanel" class="inspector-body"><h3>Pick a field</h3><p>Every control explains what it changes. Nothing is applied until review and confirmation.</p><button id="collaborator" class="wide ghost">Manage collaborator</button></div>
        <div id="catalogPanel" class="inspector-body hidden">
          <div class="catalog-row"><select id="prefix"><option>id</option><option>mod</option><option>tag</option><option>name</option></select><input id="catalogSearch" placeholder="Search the server registry" /></div>
          <div id="catalogResults" class="catalog-results"></div>
        </div>
        <div id="extensionsPanel" class="inspector-body hidden"><div id="extensions"></div></div>
        <div id="conflictPanel" class="inspector-body hidden"><h3>Priority analyzer</h3><p id="conflicts">No unresolved conflicts in this draft.</p><button id="simulateDraft" class="wide">Simulate candidate</button></div>
      </aside>
    </main>
    <section id="drawer" class="drawer" aria-label="Validation results"><div class="drawer-head"><strong>Validation and review</strong><button id="closeDrawer" class="ghost">Close</button></div><div id="drawerBody"></div></section>
    <div id="toast" role="status"></div>
  </>;
}

const root = document.getElementById("app");
if (!root) throw new Error("Editor root is missing");
render(<App />, root);
const controller = document.createElement("script");
controller.src = "/legacy.js";
controller.defer = true;
document.body.append(controller);
