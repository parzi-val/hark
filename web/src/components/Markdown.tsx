import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/** Renders Hark's markdown note bodies, styled via the .hark-md block in index.css. */
export const Markdown: React.FC<{ children: string; className?: string }> = ({ children, className }) => (
  <div className={`hark-md ${className || ''}`}>
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
  </div>
);
