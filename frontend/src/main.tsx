import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, AlertTriangle, Bot, Database, FileUp, RefreshCw, Search, Send, UploadCloud } from 'lucide-react';
import './styles.css';

type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';

type UploadedDocument = {
  id: string;
  filename: string;
  contentType?: string;
  sourceType: string;
  status: DocumentStatus;
  chunkCount: number;
  errorMessage?: string;
  createdAt: string;
  exists?: boolean;
};

type Citation = {
  documentId: string;
  filename: string;
  title: string;
  chapterPath: string;
  chunkIndex: number;
  score: number;
  preview: string;
};

type ChatResponse = {
  answer: string;
  citations: Citation[];
};

type SyncStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

type OperationalSyncRun = {
  id: string;
  source: string;
  status: SyncStatus;
  itemCount: number;
  documentId?: string;
  message?: string;
  startedAt: string;
  finishedAt: string;
};

type OperationalSyncSummary = {
  syncEnabled: boolean;
  prometheusEnabled: boolean;
  mcpLogsEnabled: boolean;
  fixedDelayMs: number;
  recentRuns: OperationalSyncRun[];
};

const api = {
  async listDocuments(): Promise<UploadedDocument[]> {
    const response = await fetch('/api/documents');
    if (!response.ok) throw new Error(await errorText(response));
    return response.json();
  },
  async upload(file: File, sourceType: string): Promise<UploadedDocument> {
    const body = new FormData();
    body.append('file', file);
    body.append('sourceType', sourceType);
    const response = await fetch('/api/documents', { method: 'POST', body });
    if (!response.ok) throw new Error(await errorText(response));
    return response.json();
  },
  async ask(question: string, topK: number): Promise<ChatResponse> {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, topK })
    });
    if (!response.ok) throw new Error(await errorText(response));
    return response.json();
  },
  async syncSummary(): Promise<OperationalSyncSummary> {
    const response = await fetch('/api/operations/sync');
    if (!response.ok) throw new Error(await errorText(response));
    return response.json();
  },
  async syncNow(): Promise<OperationalSyncRun[]> {
    const response = await fetch('/api/operations/sync', { method: 'POST' });
    if (!response.ok) throw new Error(await errorText(response));
    return response.json();
  }
};

async function errorText(response: Response) {
  try {
    const body = await response.json();
    return body.error ?? response.statusText;
  } catch {
    return response.statusText;
  }
}

function App() {
  const [documents, setDocuments] = useState<UploadedDocument[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [sourceType, setSourceType] = useState('codebase');
  const [question, setQuestion] = useState('The checkout API latency alert fired. What should I check first?');
  const [topK, setTopK] = useState(8);
  const [answer, setAnswer] = useState<ChatResponse | null>(null);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [asking, setAsking] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncSummary, setSyncSummary] = useState<OperationalSyncSummary | null>(null);
  const [error, setError] = useState('');

  const readyChunks = useMemo(
    () => documents.filter((doc) => doc.status === 'READY').reduce((sum, doc) => sum + doc.chunkCount, 0),
    [documents]
  );

  async function refreshDocuments() {
    setLoadingDocs(true);
    setError('');
    try {
      setDocuments(await api.listDocuments());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load documents');
    } finally {
      setLoadingDocs(false);
    }
  }

  async function refreshSyncSummary() {
    try {
      setSyncSummary(await api.syncSummary());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load sync status');
    }
  }

  useEffect(() => {
    refreshDocuments();
    refreshSyncSummary();
  }, []);

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!selectedFile) return;
    setUploading(true);
    setError('');
    try {
      const uploaded = await api.upload(selectedFile, sourceType);
      setSelectedFile(null);
      if (uploaded.exists) {
        setError(`${uploaded.filename} already exists, skipped upload.`);
      }
      await refreshDocuments();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  }

  async function handleAsk(event: FormEvent) {
    event.preventDefault();
    if (!question.trim()) return;
    setAsking(true);
    setError('');
    try {
      setAnswer(await api.ask(question, topK));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Question failed');
    } finally {
      setAsking(false);
    }
  }

  async function handleSyncNow() {
    setSyncing(true);
    setError('');
    try {
      await api.syncNow();
      await refreshSyncSummary();
      await refreshDocuments();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Operational sync failed');
    } finally {
      setSyncing(false);
    }
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">AI Oncall Agent</p>
          <h1>Alert, log, and codebase triage</h1>
        </div>
        <div className="status-strip">
          <Metric icon={<Database size={18} />} label="Documents" value={documents.length.toString()} />
          <Metric icon={<Search size={18} />} label="Chunks" value={readyChunks.toString()} />
        </div>
      </header>

      {error && (
        <div className="error-banner">
          <AlertTriangle size={18} />
          <span>{error}</span>
        </div>
      )}

      <section className="workspace">
        <aside className="ingestion">
          <div className="panel-head">
            <h2>Knowledge Base</h2>
            <button className="icon-button" onClick={refreshDocuments} disabled={loadingDocs} title="Refresh documents">
              <RefreshCw size={18} />
            </button>
          </div>

          <form className="upload-form" onSubmit={handleUpload}>
            <label className="dropzone">
              <UploadCloud size={26} />
              <span>{selectedFile ? selectedFile.name : 'Upload code, logs, runbooks, docs'}</span>
              <input
                type="file"
                onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
              />
            </label>

            <div className="row">
              <select value={sourceType} onChange={(event) => setSourceType(event.target.value)}>
                <option value="codebase">Codebase</option>
                <option value="logs">Logs</option>
                <option value="runbook">Runbook</option>
                <option value="design-doc">Design doc</option>
                <option value="alert">Alert payload</option>
              </select>
              <button className="primary" disabled={!selectedFile || uploading}>
                <FileUp size={18} />
                {uploading ? 'Indexing' : 'Upload'}
              </button>
            </div>
          </form>

          <div className="document-list">
            {documents.map((doc) => (
              <article className="doc-item" key={doc.id}>
                <div>
                  <strong>{doc.filename}</strong>
                  <span>{doc.sourceType} · {doc.chunkCount} chunks</span>
                </div>
                <StatusBadge status={doc.status} />
              </article>
            ))}
            {!documents.length && <p className="empty">No uploaded context yet.</p>}
          </div>

          <section className="sync-panel">
            <div className="panel-head">
              <h2>Operations Agent</h2>
              <button className="icon-button" onClick={handleSyncNow} disabled={syncing} title="Sync alerts and logs now">
                <Activity size={18} />
              </button>
            </div>
            <div className="sync-flags">
              <span className={syncSummary?.syncEnabled ? 'flag on' : 'flag'}>Scheduler</span>
              <span className={syncSummary?.prometheusEnabled ? 'flag on' : 'flag'}>Prometheus</span>
              <span className={syncSummary?.mcpLogsEnabled ? 'flag on' : 'flag'}>MCP logs</span>
            </div>
            <div className="sync-runs">
              {syncSummary?.recentRuns.slice(0, 6).map((run) => (
                <article className="sync-run" key={run.id}>
                  <div>
                    <strong>{run.source}</strong>
                    <span>{run.message ?? 'No message'}</span>
                  </div>
                  <StatusPill status={run.status} count={run.itemCount} />
                </article>
              ))}
              {!syncSummary?.recentRuns.length && <p className="empty">No live sync runs yet.</p>}
            </div>
          </section>
        </aside>

        <section className="chat">
          <form className="question-form" onSubmit={handleAsk}>
            <div className="question-box">
              <Bot size={22} />
              <textarea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                rows={4}
              />
            </div>
            <div className="question-actions">
              <label>
                Top K
                <input
                  type="number"
                  min="1"
                  max="20"
                  value={topK}
                  onChange={(event) => setTopK(Number(event.target.value))}
                />
              </label>
              <button className="primary ask" disabled={asking}>
                <Send size={18} />
                {asking ? 'Thinking' : 'Ask'}
              </button>
            </div>
          </form>

          <div className="answer-surface">
            <h2>Conversation Agent Answer</h2>
            <MarkdownAnswer content={answer ? answer.answer : 'Ask an incident question after uploading context.'} />
          </div>

          <div className="citations">
            <h2>Vector DB Citations</h2>
            <div className="citation-grid">
              {answer?.citations.map((citation) => (
                <article className="citation" key={`${citation.documentId}-${citation.chunkIndex}`}>
                  <div className="citation-top">
                    <strong>{citation.filename}</strong>
                    <span title="Retrieval rerank score">Score {citation.score.toFixed(3)}</span>
                  </div>
                  <p>{citation.title}</p>
                  <small>{citation.preview}</small>
                </article>
              ))}
            </div>
          </div>
        </section>
      </section>
    </main>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="metric">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusBadge({ status }: { status: DocumentStatus }) {
  return <span className={`badge ${status.toLowerCase()}`}>{status}</span>;
}

function StatusPill({ status, count }: { status: SyncStatus; count: number }) {
  return <span className={`badge ${status.toLowerCase()}`}>{status} · {count}</span>;
}

type MarkdownBlock =
  | { type: 'heading'; level: number; text: string; key: string }
  | { type: 'paragraph'; text: string; key: string }
  | { type: 'list'; items: string[]; key: string };

function MarkdownAnswer({ content }: { content: string }) {
  const blocks = useMemo(() => parseMarkdown(content), [content]);
  return (
    <div className="answer-text markdown-answer">
      {blocks.map((block) => {
        if (block.type === 'heading') {
          const HeadingTag = (`h${Math.min(block.level + 2, 5)}` as keyof JSX.IntrinsicElements);
          return <HeadingTag key={block.key}>{formatInline(block.text)}</HeadingTag>;
        }
        if (block.type === 'list') {
          return (
            <ul key={block.key}>
              {block.items.map((item, index) => (
                <li key={`${block.key}-${index}`}>{formatInline(item)}</li>
              ))}
            </ul>
          );
        }
        return <p key={block.key}>{formatInline(block.text)}</p>;
      })}
    </div>
  );
}

function parseMarkdown(content: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  let paragraph: string[] = [];
  let list: string[] = [];

  function flushParagraph() {
    if (!paragraph.length) return;
    blocks.push({ type: 'paragraph', text: paragraph.join(' '), key: `p-${blocks.length}` });
    paragraph = [];
  }

  function flushList() {
    if (!list.length) return;
    blocks.push({ type: 'list', items: list, key: `l-${blocks.length}` });
    list = [];
  }

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) {
      flushParagraph();
      flushList();
      continue;
    }

    const heading = /^(#{1,4})\s+(.+)$/.exec(trimmed);
    if (heading) {
      flushParagraph();
      flushList();
      blocks.push({
        type: 'heading',
        level: heading[1].length,
        text: heading[2],
        key: `h-${blocks.length}`
      });
      continue;
    }

    const bullet = /^[-*]\s+(.+)$/.exec(trimmed);
    if (bullet) {
      flushParagraph();
      list.push(bullet[1]);
      continue;
    }

    flushList();
    paragraph.push(trimmed);
  }

  flushParagraph();
  flushList();
  return blocks;
}

function formatInline(text: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  const pattern = /(`[^`]+`|\*\*[^*]+\*\*)/g;
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > cursor) {
      nodes.push(text.slice(cursor, match.index));
    }

    const token = match[0];
    if (token.startsWith('`')) {
      nodes.push(<code key={`code-${match.index}`}>{token.slice(1, -1)}</code>);
    } else {
      nodes.push(<strong key={`strong-${match.index}`}>{token.slice(2, -2)}</strong>);
    }
    cursor = match.index + token.length;
  }

  if (cursor < text.length) {
    nodes.push(text.slice(cursor));
  }
  return nodes;
}

createRoot(document.getElementById('root')!).render(<App />);
