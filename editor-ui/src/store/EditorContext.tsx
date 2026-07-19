import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { EditorApi } from "../lib/api";
import { discoverStages } from "../lib/model";
import type {
  ApplyResult,
  Bootstrap,
  CatalogPage,
  DialogState,
  DraftView,
  Notice,
  PageId,
  ReviewResult,
  StagePackage,
  StageTab,
  ValidationResult
} from "../types";

interface EditorContextValue {
  api: EditorApi;
  boot: Bootstrap | null;
  stages: StagePackage[];
  selectedStage: StagePackage | null;
  selectedStageKey: string;
  page: PageId;
  stageTab: StageTab;
  busy: string;
  error: string;
  notices: Notice[];
  dialog: DialogState | null;
  review: ReviewResult | null;
  applyResult: ApplyResult | null;
  setPage: (page: PageId) => void;
  setStageTab: (tab: StageTab) => void;
  selectStage: (key: string) => void;
  notify: (tone: Notice["tone"], title: string, message?: string) => void;
  dismissNotice: (id: number) => void;
  openDialog: (dialog: DialogState) => void;
  closeDialog: () => void;
  refresh: () => Promise<void>;
  mutateFile: (path: string, content: string | null, successMessage?: string) => Promise<void>;
  mutateFiles: (changes: Array<{ path: string; content: string | null }>, successMessage?: string) => Promise<void>;
  runDraftAction: <T>(payload: Record<string, unknown>, successMessage?: string) => Promise<T>;
  undo: () => Promise<void>;
  redo: () => Promise<void>;
  validate: () => Promise<ValidationResult | null>;
  openReview: () => Promise<void>;
  closeReview: () => void;
  apply: () => Promise<void>;
  rollback: (transaction: string) => Promise<void>;
  catalog: (catalog: string, field: string, mode: string, text: string,
            filters?: Record<string, string>, cursor?: string, pageSize?: number) => Promise<CatalogPage>;
}

const EditorContext = createContext<EditorContextValue | null>(null);
let noticeId = 0;

function mergeDraft(boot: Bootstrap, draft: Partial<DraftView>): Bootstrap {
  return { ...boot, draft: { ...boot.draft, ...draft } };
}

export function EditorProvider({ children }: { children: React.ReactNode }) {
  const apiRef = useRef<EditorApi | null>(null);
  if (!apiRef.current) apiRef.current = new EditorApi();
  const api = apiRef.current;
  const [boot, setBoot] = useState<Bootstrap | null>(null);
  const [page, setPageState] = useState<PageId>("stages");
  const [stageTab, setStageTab] = useState<StageTab>("essentials");
  const [selectedStageKey, setSelectedStageKey] = useState("");
  const [busy, setBusy] = useState("Connecting to Minecraft");
  const [error, setError] = useState("");
  const [notices, setNotices] = useState<Notice[]>([]);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [review, setReview] = useState<ReviewResult | null>(null);
  const [applyResult, setApplyResult] = useState<ApplyResult | null>(null);

  const stages = useMemo(() => discoverStages(boot?.draft.files || {}), [boot?.draft.files]);
  const selectedStage = useMemo(() => stages.find(stage => stage.key === selectedStageKey) || null,
    [stages, selectedStageKey]);

  const notify = useCallback((tone: Notice["tone"], title: string, message?: string) => {
    const notice = { id: ++noticeId, tone, title, message };
    setNotices(current => [...current.slice(-3), notice]);
    window.setTimeout(() => setNotices(current => current.filter(entry => entry.id !== notice.id)), 5000);
  }, []);

  const dismissNotice = useCallback((id: number) => {
    setNotices(current => current.filter(entry => entry.id !== id));
  }, []);

  const refresh = useCallback(async () => {
    setBusy("Refreshing the server draft");
    setError("");
    try {
      const next = await api.bootstrap();
      setBoot(next);
      setBusy("");
      setSelectedStageKey(current => current && discoverStages(next.draft.files).some(stage => stage.key === current)
        ? current
        : discoverStages(next.draft.files).find(stage => !stage.archived)?.key || "");
    } catch (failure) {
      setBusy("");
      setError(failure instanceof Error ? failure.message : "The editor could not connect to Minecraft.");
    }
  }, [api]);

  useEffect(() => { void refresh(); }, [refresh]);

  const mutateFile = useCallback(async (path: string, content: string | null, successMessage?: string) => {
    if (!boot) return;
    setBusy(`Saving ${path.split("/").pop() || path}`);
    try {
      const result = await api.request<Pick<DraftView, "revision" | "diff" | "canUndo" | "canRedo">>({
        action: "mutate",
        path,
        content,
        revision: boot.draft.revision
      });
      setBoot(current => current ? mergeDraft(current, {
        revision: result.revision,
        diff: result.diff,
        canUndo: result.canUndo,
        canRedo: result.canRedo,
        files: content == null
          ? Object.fromEntries(Object.entries(current.draft.files).filter(([key]) => key !== path))
          : { ...current.draft.files, [path]: content }
      }) : current);
      setBusy("");
      if (successMessage) notify("success", successMessage);
    } catch (failure) {
      setBusy("");
      notify("danger", "The draft was not saved", failure instanceof Error ? failure.message : String(failure));
      throw failure;
    }
  }, [api, boot, notify]);

  const mutateFiles = useCallback(async (changes: Array<{ path: string; content: string | null }>, successMessage?: string) => {
    if (!boot || !changes.length) return;
    setBusy(`Saving ${changes.length} stage files`);
    let revision = boot.draft.revision;
    let files = { ...boot.draft.files };
    let latest: Pick<DraftView, "revision" | "diff" | "canUndo" | "canRedo"> | null = null;
    try {
      for (const change of changes) {
        latest = await api.request<Pick<DraftView, "revision" | "diff" | "canUndo" | "canRedo">>({
          action: "mutate",
          path: change.path,
          content: change.content,
          revision
        });
        revision = latest.revision;
        if (change.content == null) delete files[change.path];
        else files[change.path] = change.content;
      }
      if (latest) setBoot(current => current ? mergeDraft(current, {
        revision: latest!.revision,
        diff: latest!.diff,
        canUndo: latest!.canUndo,
        canRedo: latest!.canRedo,
        files
      }) : current);
      setBusy("");
      if (successMessage) notify("success", successMessage);
    } catch (failure) {
      setBusy("");
      await refresh();
      notify("danger", "Some draft files were not saved", failure instanceof Error ? failure.message : String(failure));
      throw failure;
    }
  }, [api, boot, notify, refresh]);

  const runDraftAction = useCallback(async <T,>(payload: Record<string, unknown>, successMessage?: string): Promise<T> => {
    if (!boot) throw new Error("The editor has not connected yet.");
    setBusy("Updating the server draft");
    try {
      const result = await api.request<T>({ ...payload, revision: payload.revision ?? boot.draft.revision });
      const fresh = await api.bootstrap();
      setBoot(fresh);
      setSelectedStageKey(current => current && discoverStages(fresh.draft.files).some(stage => stage.key === current)
        ? current
        : discoverStages(fresh.draft.files).find(stage => !stage.archived)?.key || "");
      setBusy("");
      if (successMessage) notify("success", successMessage);
      return result;
    } catch (failure) {
      setBusy("");
      notify("danger", "The server rejected the change", failure instanceof Error ? failure.message : String(failure));
      throw failure;
    }
  }, [api, boot, notify]);

  const undo = useCallback(async () => {
    if (!boot?.draft.canUndo) return;
    await runDraftAction({ action: "undo" }, "The last draft change was undone");
  }, [boot?.draft.canUndo, runDraftAction]);

  const redo = useCallback(async () => {
    if (!boot?.draft.canRedo) return;
    await runDraftAction({ action: "redo" }, "The draft change was restored");
  }, [boot?.draft.canRedo, runDraftAction]);

  const validate = useCallback(async () => {
    if (!boot) return null;
    setBusy("Validating every stage");
    try {
      const result = await api.request<ValidationResult>({ action: "validate" });
      setBusy("");
      notify(result.valid ? "success" : "danger",
        result.valid ? "Every stage is valid" : "Validation found problems",
        result.valid ? `${result.stages} stages compiled successfully.` : `${result.errors.length} errors must be corrected.`);
      return result;
    } catch (failure) {
      setBusy("");
      notify("danger", "Validation failed", failure instanceof Error ? failure.message : String(failure));
      return null;
    }
  }, [api, boot, notify]);

  const openReview = useCallback(async () => {
    setBusy("Preparing the complete change review");
    try {
      const result = await api.request<ReviewResult>({ action: "review" });
      setReview(result);
      setApplyResult(null);
      setBusy("");
    } catch (failure) {
      setBusy("");
      notify("danger", "Review could not be prepared", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, notify]);

  const closeReview = useCallback(() => {
    setReview(null);
    setApplyResult(null);
  }, []);

  const apply = useCallback(async () => {
    if (!review?.validation.valid) return;
    setBusy("Applying and synchronizing the server");
    try {
      const result = await api.request<ApplyResult>({ action: "apply", confirmed: true });
      if (!result.success) throw new Error(result.explanation || "The server rejected the draft.");
      setApplyResult(result);
      const fresh = await api.bootstrap();
      setBoot(fresh);
      setBusy("");
      notify("success", "The live server is synchronized", `Server revision ${result.configurationRevision}.`);
    } catch (failure) {
      setBusy("");
      notify("danger", "Apply failed", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, notify, review?.validation.valid]);

  const rollback = useCallback(async (transaction: string) => {
    setBusy("Rolling back the transaction");
    try {
      const result = await api.request<ApplyResult>({ action: "rollback", transaction, confirmed: true });
      if (!result.success) throw new Error(result.explanation || "Rollback was rejected.");
      const fresh = await api.bootstrap();
      setBoot(fresh);
      setReview(null);
      setApplyResult(null);
      setBusy("");
      notify("success", "The transaction was rolled back", result.explanation);
    } catch (failure) {
      setBusy("");
      notify("danger", "Rollback failed", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, notify]);

  const catalog = useCallback((catalogId: string, field: string, mode: string, text: string,
                               filters: Record<string, string> = {}, cursor = "", pageSize = 50) => {
    if (!boot) return Promise.resolve({ revision: 0, entries: [], totalMatches: 0, nextCursor: "", staleRevision: false, truncated: false });
    return api.catalog(catalogId, field, mode, text, boot.catalog.revision, filters, cursor, pageSize);
  }, [api, boot]);

  const setPage = useCallback((next: PageId) => setPageState(next), []);
  const selectStage = useCallback((key: string) => {
    setSelectedStageKey(key);
    setPageState("stages");
    setStageTab("essentials");
  }, []);

  const value = useMemo<EditorContextValue>(() => ({
    api, boot, stages, selectedStage, selectedStageKey, page, stageTab, busy, error, notices, dialog,
    review, applyResult, setPage, setStageTab, selectStage, notify, dismissNotice,
    openDialog: setDialog, closeDialog: () => setDialog(null), refresh, mutateFile, mutateFiles,
    runDraftAction, undo, redo, validate, openReview, closeReview, apply, rollback, catalog
  }), [api, boot, stages, selectedStage, selectedStageKey, page, stageTab, busy, error, notices, dialog,
    review, applyResult, setPage, selectStage, notify, dismissNotice, refresh, mutateFile, mutateFiles,
    runDraftAction, undo, redo, validate, openReview, closeReview, apply, rollback, catalog]);

  return <EditorContext.Provider value={value}>{children}</EditorContext.Provider>;
}

export function useEditor(): EditorContextValue {
  const value = useContext(EditorContext);
  if (!value) throw new Error("useEditor must be used inside EditorProvider");
  return value;
}
