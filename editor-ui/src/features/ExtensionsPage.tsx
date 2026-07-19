import { Badge, EmptyState } from "../components/ui";
import { Icon } from "../components/Icon";
import { title } from "../lib/model";
import { useEditor } from "../store/EditorContext";

export function ExtensionsPage() {
  const { boot } = useEditor();
  const registrations = boot?.extensions.registrations || [];
  return <div className="extensions-page"><header className="page-heading"><div><span className="eyebrow">Open extension surface</span><h1>Java and KubeJS extensions</h1><p>Features registered at runtime appear here and stay available to the source editor and server compiler without hardcoding them into the React app.</p></div><Badge tone="gold">Revision {boot?.extensions.revision || 0}</Badge></header>
    <section className="capability-cloud"><header><Icon name="extensions"/><div><h2>Runtime capabilities</h2><p>The connected server advertises these supported contracts.</p></div></header><div>{boot?.capabilities.map(capability => <Badge key={capability}>{title(capability)}</Badge>)}</div></section>
    {registrations.length ? <div className="extension-grid">{registrations.map(registration => <article key={registration.id}><header><span>✦</span><div><strong>{registration.title}</strong><code>{registration.id}</code></div><Badge tone="gold">{title(registration.kind)}</Badge></header><p>{registration.description || "Registered by the running server."}</p><div className="extension-arguments">{registration.arguments?.map(argument => <div key={argument.name}><span>{argument.name}</span><code>{argument.type}</code>{argument.required ? <Badge tone="warning">Required</Badge> : <Badge>Optional</Badge>}</div>)}</div></article>)}</div> : <EmptyState icon="extensions" title="No custom extension forms registered" description="The built in Java API and KubeJS events remain available. Registered editor descriptions appear here automatically."/>}
  </div>;
}
