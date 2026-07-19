export type PageId = "stages" | "layout" | "settings" | "registry" | "extensions";
export type StageTab = "essentials" | "rules" | "progression" | "effects" | "advanced" | "source";

export interface DraftDiffEntry {
  path: string;
  change: "ADDED" | "MODIFIED" | "DELETED";
  beforeBytes: number;
  afterBytes: number;
}

export interface DraftView {
  id: string;
  owner: string;
  revision: number;
  baseConfigurationRevision: number;
  baseCatalogRevision: number;
  files: Record<string, string>;
  baseFiles: Record<string, string>;
  diff: DraftDiffEntry[];
  canUndo: boolean;
  canRedo: boolean;
  updatedAt: number;
  collaborators: string[];
}

export interface SessionView {
  id: string;
  owner: string;
  draftId: string;
  createdAt: number;
  expiresAt: number;
  lastAccessAt: number;
  baseConfigurationRevision: number;
}

export interface FieldSchema {
  id: string;
  file: string;
  path: string;
  label: string;
  help: string;
  type: "BOOLEAN" | "INTEGER" | "DECIMAL" | "STRING" | "LIST" | "ENUM" | string;
  defaultValue: unknown;
  required: boolean;
  catalogId?: string;
  prefixModes: string[];
  enumValues: string[];
  capabilities: string[];
  restartRequirement: string;
  controlHints: Record<string, unknown>;
}

export interface ExtensionArgument {
  name: string;
  type: string;
  required: boolean;
  defaultValue?: unknown;
  catalog?: string;
}

export interface ExtensionRegistration {
  id: string;
  title: string;
  kind: string;
  description?: string;
  arguments?: ExtensionArgument[];
}

export interface Bootstrap {
  protocol: number;
  session: SessionView;
  draft: DraftView;
  schemas: FieldSchema[];
  extensions: { registrations?: ExtensionRegistration[]; revision?: number };
  capabilities: string[];
  catalog: {
    revision: number;
    configurationRevision: number;
    ids: string[];
    checksum: string;
    providerErrors: string[];
  };
}

export interface CatalogEntry {
  catalogId: string;
  key: string;
  registryId?: string;
  namespace: string;
  label: string;
  translationKey: string;
  sourceType: string;
  modId: string;
  modName: string;
  tags: string[];
  capabilities: string[];
  metadata: Record<string, unknown>;
}

export interface CatalogPage {
  revision: number;
  entries: CatalogEntry[];
  totalMatches: number;
  nextCursor: string;
  staleRevision: boolean;
  truncated: boolean;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  stages: number;
  revision: number;
}

export interface ReviewResult {
  revision: number;
  diff: DraftDiffEntry[];
  validation: ValidationResult;
}

export interface ApplyResult {
  success: boolean;
  transactionId: string;
  configurationRevision: number;
  diff: DraftDiffEntry[];
  validation: ValidationResult;
  explanation: string;
  code: string;
}

export interface StagePackage {
  key: string;
  folder: string;
  stagePath: string;
  rulesPath: string;
  progressionPath: string;
  legacy: boolean;
  archived: boolean;
  id: string;
  name: string;
  description: string;
  icon: string;
  category: string;
  color: string;
  hidden: boolean;
  ruleCount: number;
  grantCount: number;
  revokeCount: number;
  dependencies: string[];
  dependencyMode: string;
  dependencyCount: number;
}

export interface RuleModel {
  table: "rules" | "temporary_rules" | "classic";
  tableIndex: number;
  category: string;
  action: string;
  effect: string;
  selector: string;
  priority: number;
  viewer: string;
  lifetime: string;
  duration: string;
  conditionType: string;
  conditionTarget: string;
  count: number;
  exception: string;
  exceptionPriority: number;
  sourceText: string;
}

export interface ProgressionModel {
  kind: "grants" | "revokes";
  tableIndex: number;
  conditionType: string;
  conditionTarget: string;
  count: number;
  repeat: string;
  scope: string;
  priority: number;
  cooldown: string;
  sourceText: string;
}

export interface Notice {
  id: number;
  tone: "success" | "warning" | "danger" | "info";
  title: string;
  message?: string;
}

export interface DialogState {
  title: string;
  description?: string;
  content: React.ReactNode;
  width?: "compact" | "standard" | "wide";
}
