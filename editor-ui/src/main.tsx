import { render } from "preact";
import "./editor.css";

function App() {
  return <>
    <header class="topbar">
      <div class="brand"><span class="lock">◆</span><div><strong>ProgressiveStages</strong><small>Stage builder</small></div></div>
      <div class="status"><span id="connection" class="pill pending">Connecting</span><span id="revision" class="muted"></span></div>
      <div class="actions"><button id="undo" class="ghost">Undo</button><button id="redo" class="ghost">Redo</button><button id="validate">Check my work</button><button id="review" class="primary">Review and apply</button></div>
    </header>
    <main class="workspace">
      <aside class="navigator panel">
        <div class="panel-title"><div><span>Stages</span><small>Choose what players unlock</small></div><button id="newStage" class="icon-button" aria-label="Create a stage">＋</button></div>
        <form id="newStagePanel" class="new-stage-panel hidden">
          <strong>Create a stage</strong>
          <label>Stage name<input id="newStageName" autocomplete="off" placeholder="Iron Age" required /></label>
          <details><summary>Pack name</summary><label>Namespace<input id="newStageNamespace" value="pack" pattern="[a-z0-9_.-]+" /></label></details>
          <small id="newStagePreview">Saved as pack:iron_age</small>
          <div class="inline-actions"><button type="button" id="cancelStage" class="ghost">Cancel</button><button type="submit" class="primary">Create stage</button></div>
        </form>
        <input id="fileSearch" type="search" placeholder="Search stages" aria-label="Search stages" />
        <nav id="files" class="stage-list" aria-label="Stages"></nav>
        <details class="stage-tools">
          <summary>Stage tools</summary>
          <div class="stage-actions">
            <button id="duplicateStage" class="ghost">Duplicate</button>
            <button id="renameStage" class="ghost">Rename</button>
            <button id="moveStage" class="ghost">Move</button>
            <button id="archiveStage" class="ghost">Archive or restore</button>
            <button id="exportStage" class="ghost">Export</button>
            <button id="importStage" class="ghost">Import</button>
            <button id="deleteStage" class="ghost danger">Delete</button>
          </div>
        </details>
        <button id="settings" class="wide ghost">Main settings</button>
      </aside>
      <section class="canvas panel">
        <div class="tabs"><button data-view="form" class="active">Easy builder</button><button data-view="graph">Player UI layout</button><button data-view="source">TOML source</button></div>
        <div id="breadcrumb" class="breadcrumb"></div>
        <div id="formView" class="view"></div>
        <div id="graphView" class="view hidden"><div class="graph-help"><div><strong>Player progression UI layout.</strong><span> Drag every icon exactly where it should appear in Minecraft. Beginner paths start at the bottom.</span></div><div class="graph-controls"><select id="graphCategory" aria-label="Graph category"><option value="">All categories</option></select><input id="graphSearch" placeholder="Find a stage" aria-label="Find a stage"/><span class="graph-zoom"><button id="graphZoomOut" class="ghost" aria-label="Zoom out">−</button><output id="graphZoomValue">100%</output><button id="graphZoomIn" class="ghost" aria-label="Zoom in">+</button></span><button id="fitGraph" class="ghost">Fit graph</button><button id="resetGraphLayout" class="ghost">Use automatic layout</button><button id="autoArrangeGraph" class="ghost">Arrange and save</button></div></div><div id="graphStatus" class="graph-status"></div><div id="graphViewport" class="graph-viewport"><div id="graph" class="graph"></div></div></div>
        <div id="sourceView" class="view source-view hidden"><div id="sourceFiles" class="source-file-tabs"></div><textarea id="source" spellcheck={false} aria-label="TOML source"></textarea><div class="source-actions"><span id="dirty" class="muted"></span><button id="saveSource" class="primary">Save source to draft</button></div></div>
      </section>
      <aside class="inspector panel">
        <div class="panel-title"><div><span>Inspector</span><small>Help without leaving the page</small></div><button id="theme" class="ghost" aria-label="Toggle theme">◐</button></div>
        <div class="inspector-tabs"><button data-inspector="help" class="active">Help</button><button data-inspector="catalog">Registry</button><button data-inspector="extensions">Extensions</button><button data-inspector="conflicts">Conflicts</button></div>
        <div id="helpPanel" class="inspector-body"><h3>Pick a stage</h3><p>Use the plus buttons in the builder. Every choice is translated into valid TOML for you.</p><button id="collaborator" class="wide ghost">Manage collaborator</button></div>
        <div id="catalogPanel" class="inspector-body hidden">
          <div class="catalog-row"><select id="prefix"><option value="id">Exact ID</option><option value="mod">Whole mod</option><option value="tag">Tag</option><option value="name">Name match</option></select><input id="catalogSearch" placeholder="Search the server registry" /></div>
          <div id="catalogResults" class="catalog-results"></div>
        </div>
        <div id="extensionsPanel" class="inspector-body hidden"><div id="extensions"></div></div>
        <div id="conflictPanel" class="inspector-body hidden"><h3>Priority analyzer</h3><p id="conflicts">No unresolved conflicts in this draft.</p><button id="simulateDraft" class="wide">Simulate candidate</button></div>
      </aside>
    </main>
    <section id="drawer" class="drawer" aria-label="Validation results"><div class="drawer-head"><strong>Validation and review</strong><button id="closeDrawer" class="ghost">Close</button></div><div id="drawerBody"></div></section>
    <div id="modalBackdrop" class="modal-backdrop hidden"><section id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="modalTitle"></section></div>
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
