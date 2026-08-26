/** Flattens markdown to plain text for truncated previews (stream / grid snippets). */
export function stripMarkdown(s: string): string {
  return (s || '')
    .replace(/```[\s\S]*?```/g, ' ')          // fenced code
    .replace(/`([^`]+)`/g, '$1')              // inline code
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')      // images
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')   // links → text
    .replace(/^#{1,6}\s+/gm, '')               // headings
    .replace(/^\s*[-*+]\s+/gm, '')             // bullets
    .replace(/^\s*\d+\.\s+/gm, '')             // ordered list
    .replace(/^\s*>\s?/gm, '')                 // blockquote
    .replace(/(\*\*|__)(.*?)\1/g, '$2')        // bold
    .replace(/(\*|_)(.*?)\1/g, '$2')           // italic
    .replace(/~~(.*?)~~/g, '$2')               // strikethrough
    .replace(/\s+/g, ' ')
    .trim();
}
