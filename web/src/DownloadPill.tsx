// Where the download goes once releases are published (docs/workstreams/website.md W2).
export function DownloadPill() {
  return (
    <div className="flex flex-col items-center gap-3">
      <span className="inline-flex h-[54px] items-center gap-3 rounded-[27px] border border-ink-field-border bg-ink-elevated/80 px-6 font-mono text-[13px] text-bone-dim backdrop-blur-md">
        <span className="relative flex size-2">
          <span className="absolute inset-0 animate-ping rounded-full bg-crumb-mid opacity-60 motion-reduce:animate-none" />
          <span className="relative size-2 rounded-full bg-crumb-mid" />
        </span>
        android · download soon
      </span>
      <span className="font-mono text-[11px] text-ink-outline">android 10 and up · free · no account</span>
    </div>
  )
}
