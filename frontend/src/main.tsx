import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AlertTriangle, Bot, Database, FileUp, RefreshCw, Search, Send, UploadCloud } from 'lucide-react';
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

  useEffect(() => {
    refreshDocuments();
  }, []);

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!selectedFile) return;
    setUploading(true);
    setError('');
    try {
      await api.upload(selectedFile, sourceType);
      setSelectedFile(null);
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
            <h2>Knowledge</h2>
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
            <h2>Answer</h2>
            <div className="answer-text">
              {answer ? answer.answer : 'Ask an incident question after uploading context.'}
            </div>
          </div>

          <div className="citations">
            <h2>Citations</h2>
            <div className="citation-grid">
              {answer?.citations.map((citation) => (
                <article className="citation" key={`${citation.documentId}-${citation.chunkIndex}`}>
                  <div className="citation-top">
                    <strong>{citation.filename}</strong>
                    <span>{citation.score.toFixed(3)}</span>
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

createRoot(document.getElementById('root')!).render(<App />);
