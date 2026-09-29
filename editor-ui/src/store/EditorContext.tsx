import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { EditorApi, EditorApiError } from "../lib/api";
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
  hasLocalErrors: boolean;
  dialog: DialogState | null;
  review: ReviewResult | null;
  validationResult: ValidationResult | null;
  applyResult: ApplyResult | null;
  setPage: (page: PageId) => void;
  setStageTab: (tab: StageTab) => void;
  selectStage: (key: string) => void;
  notify: (tone: Notice["tone"], title: string, message?: string) => void;
  setLocalError: (field: string, message: string | null) => void;
  dismissNotice: (id: number) => void;
  openDialog: (dialog: DialogState) => void;
  closeDialog: () => void;
  refresh: () => Promise<void>;
  mutateFile: (path: string, content: string | null | ((current: string) => string | null), successMessage?: string) => Promise<void>;
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
  const bootRef = useRef<Bootstrap | null>(null);
  bootRef.current = boot;
  const mutationQueueRef = useRef<Promise<void>>(Promise.resolve());
  const [page, setPageState] = useState<PageId>("stages");
  const [stageTab, setStageTab] = useState<StageTab>("essentials");
  const [selectedStageKey, setSelectedStageKey] = useState("");
  const [busy, setBusy] = useState("Connecting to Minecraft");
  const [error, setError] = useState("");
  const [notices, setNotices] = useState<Notice[]>([]);
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({});
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [review, setReview] = useState<ReviewResult | null>(null);
  const [applyResult, setApplyResult] = useState<ApplyResult | null>(null);

  const [validationState, setValidationState] = useState<{ draftId: string; result: ValidationResult } | null>(null);
  const rememberValidation = useCallback((result: ValidationResult | undefined, draftId: string | undefined) => {
    if (!result || !draftId) return;
    setValidationState(current => current?.draftId === draftId
      && (current.result.validatedRevision ?? current.result.revision ?? -1) > (result.validatedRevision ?? result.revision ?? -1)
      ? current : { draftId, result });
  }, []);
  const validationResult = validationState?.draftId === boot?.draft.id
    && (validationState?.result.validatedRevision ?? validationState?.result.revision) === boot?.draft.revision
    ? validationState?.result ?? null : null;

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

  const setLocalError = useCallback((field: string, message: string | null) => {
    setLocalErrors(current => {
      if (!message) {
        if (!(field in current)) return current;
        const next = { ...current };
        delete next[field];
        return next;
      }
      return current[field] === message ? current : { ...current, [field]: message };
    });
  }, []);

  const refresh = useCallback(async () => {
    setBusy("Refreshing the server draft");
    setError("");
    try {
      const next = await api.bootstrap();
      setBoot(next);
      rememberValidation(next.validation, next.draft.id);
      setBusy("");
      setSelectedStageKey(current => current && discoverStages(next.draft.files).some(stage => stage.key === current)
        ? current
        : discoverStages(next.draft.files).find(stage => !stage.archived)?.key || "");
    } catch (failure) {
      setBusy("");
      setError(failure instanceof Error ? failure.message : "The editor could not connect to Minecraft.");
    }
  }, [api, rememberValidation]);

  useEffect(() => { void refresh(); }, [refresh]);

  const mutateFile = useCallback((path: string, update: string | null | ((current: string) => string | null), successMessage?: string) => {
    const task = mutationQueueRef.current.then(async () => {
      const current = bootRef.current;
      if (!current) return;
      const content = typeof update === "function" ? update(current.draft.files[path] || "") : update;
      setBusy(`Saving ${path.split("/").pop() || path}`);
      try {
        const result = await api.request<Pick<DraftView, "revision" | "diff" | "canUndo" | "canRedo">>({
          action: "mutate",
          path,
          content,
          revision: current.draft.revision
        });
        const files = content == null
          ? Object.fromEntries(Object.entries(current.draft.files).filter(([key]) => key !== path))
          : { ...current.draft.files, [path]: content };
        const next = mergeDraft(current, {
          revision: result.revision,
          diff: result.diff,
          canUndo: result.canUndo,
          canRedo: result.canRedo,
          files
        });
        bootRef.current = next;
        setBoot(next);
        setBusy("");
        if (successMessage) notify("success", successMessage);
      } catch (failure) {
        setBusy("");
        try {
          const fresh = await api.bootstrap();
          bootRef.current = fresh;
          setBoot(fresh);
          rememberValidation(fresh.validation, fresh.draft.id);
          setSelectedStageKey(key => key && discoverStages(fresh.draft.files).some(stage => stage.key === key)
            ? key
            : discoverStages(fresh.draft.files).find(stage => !stage.archived)?.key || "");
        } catch (_) {
          // Keep the existing draft visible when the recovery bootstrap also fails.
        }
        notify("danger", "The draft was not saved", failure instanceof Error ? failure.message : String(failure));
        throw failure;
      }
    });
    mutationQueueRef.current = task.catch(() => {});
    return task;
  }, [api, notify, rememberValidation]);

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
      rememberValidation(fresh.validation, fresh.draft.id);
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
  }, [api, boot, notify, rememberValidation]);

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
    if (Object.keys(localErrors).length) {
      notify("danger", "Fix editor fields first", "Resolve the highlighted setting errors before validating the draft.");
      return null;
    }
    setBusy("Validating every stage");
    try {
      const result = await api.request<ValidationResult>({ action: "validate" });
      rememberValidation(result, boot.draft.id);
      setBusy("");
      if (bootRef.current?.draft.id !== boot.draft.id
        || (result.validatedRevision ?? result.revision) !== bootRef.current?.draft.revision) return result;
      notify(result.valid ? "success" : "danger",
        result.valid ? "Every stage is valid" : "Validation found problems",
        result.valid ? `${result.stages} stages compiled successfully.` : `${result.errors.length} errors must be corrected.`);
      return result;
    } catch (failure) {
      setBusy("");
      notify("danger", "Validation failed", failure instanceof Error ? failure.message : String(failure));
      return null;
    }
  }, [api, boot, localErrors, notify, rememberValidation]);

  const openReview = useCallback(async () => {
    if (Object.keys(localErrors).length) {
      notify("danger", "Fix editor fields first", "Resolve the highlighted setting errors before opening review.");
      return;
    }
    setBusy("Preparing the complete change review");
    try {
      const result = await api.request<ReviewResult>({ action: "review" });
      setReview(result);
      rememberValidation(result.validation, boot?.draft.id);
      setApplyResult(null);
      setBusy("");
    } catch (failure) {
      setBusy("");
      notify("danger", "Review could not be prepared", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, boot?.draft.id, localErrors, notify, rememberValidation]);

  const closeReview = useCallback(() => {
    setReview(null);
    setApplyResult(null);
  }, []);

  const apply = useCallback(async () => {
    if (!review?.validation.valid || Object.keys(localErrors).length) return;
    setBusy("Applying and synchronizing the server");
    try {
      const result = await api.request<ApplyResult>({ action: "apply", revision: review.revision, confirmed: true });
      rememberValidation(result.validation, boot?.draft.id);
      if (!result.success) {
        if (result.validation) setReview(current => current ? { ...current, validation: result.validation } : current);
        throw new Error(result.explanation || "The server rejected the draft.");
      }
      setApplyResult(result);
      const fresh = await api.bootstrap();
      setBoot(fresh);
      rememberValidation(fresh.validation, fresh.draft.id);
      setBusy("");
      notify("success", result.restartRequired ? "Settings saved with restart pending" : "The live server is synchronized",
        result.restartRequired ? `Restart to activate: ${result.pendingRestartSettings?.join(", ") || "some settings"}.` : `Server revision ${result.configurationRevision}.`);
    } catch (failure) {
      if (failure instanceof EditorApiError && failure.code === "draft_conflict") {
        setReview(null);
        setApplyResult(null);
        await refresh();
      }
      setBusy("");
      notify("danger", "Apply failed", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, boot?.draft.id, localErrors, notify, refresh, rememberValidation, review]);

  const rollback = useCallback(async (transaction: string) => {
    setBusy("Rolling back the transaction");
    try {
      const result = await api.request<ApplyResult>({ action: "rollback", transaction, confirmed: true });
      if (!result.success) throw new Error(result.explanation || "Rollback was rejected.");
      const fresh = await api.bootstrap();
      setBoot(fresh);
      rememberValidation(fresh.validation, fresh.draft.id);
      setReview(null);
      setApplyResult(null);
      setBusy("");
      notify("success", "The transaction was rolled back", result.explanation);
    } catch (failure) {
      setBusy("");
      notify("danger", "Rollback failed", failure instanceof Error ? failure.message : String(failure));
    }
  }, [api, notify, rememberValidation]);

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
    api, boot, stages, selectedStage, selectedStageKey, page, stageTab, busy, error, notices,
    hasLocalErrors: Object.keys(localErrors).length > 0, dialog,
    review, validationResult, applyResult, setPage, setStageTab, selectStage, notify, dismissNotice, setLocalError,
    openDialog: setDialog, closeDialog: () => setDialog(null), refresh, mutateFile, mutateFiles,
    runDraftAction, undo, redo, validate, openReview, closeReview, apply, rollback, catalog
  }), [api, boot, stages, selectedStage, selectedStageKey, page, stageTab, busy, error, notices, dialog,
    localErrors, review, validationResult, applyResult, setPage, selectStage, notify, dismissNotice, setLocalError, refresh, mutateFile, mutateFiles,
    runDraftAction, undo, redo, validate, openReview, closeReview, apply, rollback, catalog]);

  return <EditorContext.Provider value={value}>{children}</EditorContext.Provider>;
}

export function useEditor(): EditorContextValue {
  const value = useContext(EditorContext);
  if (!value) throw new Error("useEditor must be used inside EditorProvider");
  return value;
}
