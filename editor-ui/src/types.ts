export type PageId = "stages" | "layout" | "settings" | "registry" | "extensions";
export type StageTab = "essentials" | "rules" | "progression" | "effects" | "advanced" | "integrations" | "source";

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

export interface StageCapabilities {
  teamProvider: "ABSENT" | "DISABLED" | "READY";
  luckPerms: "ABSENT" | "DISABLED" | "STARTING" | "READY" | "FAILED";
  supportedOwnership: string[];
  supportedInboundModes: string[];
  configuredGroupStatus: Record<string, "PRESENT" | "MISSING" | "UNKNOWN">;
  configuredCommandStatus: Record<string, "RESOLVED" | "MISSING" | "AMBIGUOUS">;
  definitionRevision: number;
}

export interface Bootstrap {
  protocol: number;
  stageCapabilities?: StageCapabilities;
  teamMode?: string;
  validation?: ValidationResult;
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

export interface FieldDiagnostic {
  severity: "ERROR" | "WARNING";
  file: string;
  field: string;
  ruleId?: string;
  code: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  stages: number;
  validatedRevision?: number;
  revision?: number;
  diagnostics?: FieldDiagnostic[];
  stageCapabilities?: StageCapabilities;
  teamMode?: string;
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
  restartRequired?: boolean;
  pendingRestartSettings?: string[];
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

export interface InboundModel {
  id: string;
  groups: string[];
  permissions: string[];
  match: "all" | "any";
  contexts: Record<string, string[]>;
  contextSourceError?: string;
  sourceText?: string;
}

export interface OutboundModel {
  id: string;
  kind: "group" | "permission";
  value: string;
  contexts: Record<string, string[]>;
  contextSourceError?: string;
  sourceText?: string;
}

export interface CommandPermissionModel {
  id: string;
  path: string;
  descendants: boolean;
  sourceText?: string;
}

export interface InteractionModel {
  type: "item_on_block" | "block_right_click" | "item_on_entity" | "item_into_inventory" | string;
  heldItem: string;
  targetBlock: string;
  targetEntity: string;
  targetKind: string;
  target: string;
  effect: string;
  priority: number;
  description: string;
  sourceText?: string;
}

export interface RuleModel {
  classicField?: string;
  stageState?: string;
  table: "rules" | "temporary_rules" | "classic" | "recipe_items" | "recipe_ids" | "interactions";
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
  id?: string;
  recipeKind?: "output" | "identifier";
  targetKind?: "block" | "menu" | "inventory";
  destination?: string;
  resetConditionType?: string;
  resetConditionTarget?: string;
  resetCount?: number;
  conditionSource?: string;
  resetConditionSource?: string;
  ambiguous?: boolean;
  ineffectivePriority?: boolean;
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
