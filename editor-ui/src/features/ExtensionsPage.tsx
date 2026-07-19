import { Badge, EmptyState } from "../components/ui";
import { Icon } from "../components/Icon";
import { title } from "../lib/model";
import { useEditor } from "../store/EditorContext";

export function ExtensionsPage() {
  const { boot } = useEditor();
  const registrations = boot?.extensions.registrations || [];
  return <div className="extensions-page"><header className="page-heading"><div><h1>Extensions</h1><p>Java and KubeJS features registered by the connected server.</p></div></header>
    <section className="capability-cloud"><header><Icon name="extensions"/><div><h2>Runtime capabilities</h2><p>The connected server advertises these supported contracts.</p></div></header><div>{boot?.capabilities.map(capability => <Badge key={capability}>{title(capability)}</Badge>)}</div></section>
    {registrations.length ? <div className="extension-grid">{registrations.map(registration => <article key={registration.id}><header><span>✦</span><div><strong>{registration.title}</strong><code>{registration.id}</code></div><Badge tone="gold">{title(registration.kind)}</Badge></header><p>{registration.description || "Registered by the running server."}</p><div className="extension-arguments">{registration.arguments?.map(argument => <div key={argument.name}><span>{argument.name}</span><code>{argument.type}</code>{argument.required ? <Badge tone="warning">Required</Badge> : <Badge>Optional</Badge>}</div>)}</div></article>)}</div> : <EmptyState icon="extensions" title="No custom extension forms registered" description="The built in Java API and KubeJS events remain available. Registered editor descriptions appear here automatically."/>}
  </div>;
}
