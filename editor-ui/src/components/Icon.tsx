const paths: Record<string, React.ReactNode> = {
  home: <><path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10.5V21h13V10.5"/><path d="M9.5 21v-6h5v6"/></>,
  stages: <><path d="M5 19h4v-4H5zM15 9h4V5h-4zM15 19h4v-4h-4z"/><path d="M9 17h3a3 3 0 0 0 3-3v-3M9 7h3a3 3 0 0 1 3 3v5M5 7h10"/></>,
  layout: <><circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="18" r="2.5"/><circle cx="12" cy="6" r="2.5"/><path d="m7.5 16 3-7.5M16.5 16l-3-7.5"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/></>,
  extensions: <><path d="M8.5 3v5.5H3M15.5 21v-5.5H21M21 8.5h-5.5V3M3 15.5h5.5V21"/><path d="M8.5 8.5h7v7h-7z"/></>,
  plus: <path d="M12 5v14M5 12h14"/>,
  check: <path d="m4 12 5 5L20 6"/>,
  undo: <><path d="m8 7-5 5 5 5"/><path d="M4 12h10a6 6 0 0 1 6 6"/></>,
  redo: <><path d="m16 7 5 5-5 5"/><path d="M20 12H10a6 6 0 0 0-6 6"/></>,
  arrow: <path d="m9 18 6-6-6-6"/>,
  close: <path d="M6 6l12 12M18 6 6 18"/>,
  dots: <><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></>,
  warning: <><path d="M12 3 2.5 20h19z"/><path d="M12 9v5M12 17.2v.1"/></>,
  file: <><path d="M6 2.8h8l4 4V21H6z"/><path d="M14 2.8v4h4M9 12h6M9 16h6"/></>,
  rules: <><path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="10" cy="18" r="2"/></>,
  progression: <><path d="M5 19 12 5l7 14"/><path d="M8 14h8"/></>,
  gift: <><path d="M4 10h16v11H4zM3 7h18v3H3zM12 7v14"/><path d="M12 7c-1.5-4-6-4-6-1 0 1.5 2 1 6 1ZM12 7c1.5-4 6-4 6-1 0 1.5-2 1-6 1Z"/></>,
  code: <><path d="m9 6-6 6 6 6M15 6l6 6-6 6"/></>,
  spark: <><path d="m12 2 1.4 5.6L19 9l-5.6 1.4L12 16l-1.4-5.6L5 9l5.6-1.4z"/><path d="m19 15 .7 2.3L22 18l-2.3.7L19 21l-.7-2.3L16 18l2.3-.7z"/></>
};

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.spark}</svg>;
}
