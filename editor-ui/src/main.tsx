import { Component, type ErrorInfo, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./editor.css";
import { EditorProvider } from "./store/EditorContext";

class ErrorBoundary extends Component<{ children: ReactNode }, { error: string }> {
  state = { error: "" };
  static getDerivedStateFromError(error: Error) { return { error: error.message || "The React editor stopped unexpectedly." }; }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error("ProgressiveStages editor failure", error, info); }
  render() {
    if (this.state.error) return <main className="loading-screen error-screen"><img src="/logo.png" alt="ProgressiveStages lock logo"/><h1>The editor needs to restart</h1><p>{this.state.error}</p><button className="button button-primary" onClick={() => window.location.reload()}>Reload editor</button></main>;
    return this.props.children;
  }
}

const root = document.getElementById("app");
if (!root) throw new Error("Editor root is missing");
createRoot(root).render(<ErrorBoundary><EditorProvider><App/></EditorProvider></ErrorBoundary>);
