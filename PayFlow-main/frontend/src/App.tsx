import React, { useState, useEffect, useRef } from 'react'
import {
  Wallet,
  ArrowRight,
  TrendingUp,
  ShieldCheck,
  ShieldAlert,
  Loader2,
  RefreshCw,
  PlusCircle,
  Copy,
  CheckCircle,
  Tag,
  User,
  Lock,
  ListRestart,
  Check,
  X
} from 'lucide-react'

// Interface definitions matching Spring Boot records
interface Account {
  id: string
  ownerName: string
  balance: number
  currency: string
}

interface LedgerEntry {
  id: string
  transactionId: string
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  description: string
  merchant: string
  category: string
  recordedAt: string
  chainIndex: number
  previousHash: string
  entryHash: string
}

interface VerificationResult {
  valid: boolean
  error?: string
  offendingIndex?: number
}

interface SimulatedRequestLog {
  id: number
  label: string
  timestamp: string
  status: 'PENDING' | 'SUCCESS' | 'IDEMPOTENT_BLOCK' | 'FAILED'
  details?: string
}

interface ToastNotification {
  id: string
  type: 'success' | 'error' | 'info'
  title: string
  message: string
}



export default function App() {
  // Application State
  const [accounts, setAccounts] = useState<Account[]>([])
  const [ledger, setLedger] = useState<LedgerEntry[]>([])
  const [verification, setVerification] = useState<VerificationResult | null>(null)
  
  // Loading States
  const [loadingAccounts, setLoadingAccounts] = useState(false)
  const [loadingLedger, setLoadingLedger] = useState(false)
  const [verifyingLedger, setVerifyingLedger] = useState(false)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [toasts, setToasts] = useState<ToastNotification[]>([])

  // Form States
  const [newAccountName, setNewAccountName] = useState('')
  const [newAccountBalance, setNewAccountBalance] = useState('1000')
  const [newAccountCurrency, setNewAccountCurrency] = useState('INR')

  const [transferFrom, setTransferFrom] = useState('')
  const [transferTo, setTransferTo] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferDesc, setTransferDesc] = useState('')
  const [transferMerchant, setTransferMerchant] = useState('')
  
  // Copy state
  const [copiedId, setCopiedId] = useState<string | null>(null)

  // --- Pure UUID Idempotency States ---
  // Each new transfer generates a fresh crypto.randomUUID(); retries reuse the same one.
  const [simLogs, setSimLogs] = useState<SimulatedRequestLog[]>([])
  const [simPanelOpen, setSimPanelOpen] = useState(false)
  const [pendingIdempotencyKey, setPendingIdempotencyKey] = useState<string | null>(null)
  const [lastTransferPayload, setLastTransferPayload] = useState<{
    from: string; to: string; amount: number; merchant: string; desc: string
  } | null>(null)
  const [simulatingError, setSimulatingError] = useState(false)
  const succeededTxnIdsRef = useRef<Set<string>>(new Set())

  // Auto-fetch on mount
  useEffect(() => {
    localStorage.removeItem('payflow_account_ids')
    loadData()
  }, [])



  // Toast Helper
  const addToast = (type: 'success' | 'error' | 'info', title: string, message: string) => {
    const id = Math.random().toString(36).substring(2, 9)
    setToasts(prev => [...prev, { id, type, title, message }])
    setTimeout(() => {
      removeToast(id)
    }, 6000)
  }

  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id))
  }

  // Core API loader
  const loadData = async () => {
    setLoadingLedger(true)
    try {
      // 1. Fetch all ledger entries
      const ledgerRes = await fetch('/ledger')
      if (!ledgerRes.ok) throw new Error('Failed to fetch ledger logs')
      const ledgerData: LedgerEntry[] = await ledgerRes.json()
      // Sort ledger entries by chainIndex or recordedAt descending
      const sortedLedger = ledgerData.sort((a, b) => b.chainIndex - a.chainIndex)
      setLedger(sortedLedger)

      // 2. Discover all unique account IDs from ledger and localStorage registry
      const localAccountIds: string[] = JSON.parse(localStorage.getItem('payflow_account_ids') || '[]')
      const ledgerAccountIds = new Set<string>()
      sortedLedger.forEach(entry => {
        if (entry.fromAccount) ledgerAccountIds.add(entry.fromAccount)
        if (entry.toAccount) ledgerAccountIds.add(entry.toAccount)
      })
      
      const allUniqueIds = Array.from(new Set([...localAccountIds, ...Array.from(ledgerAccountIds)]))

      // 3. Fetch latest details for all unique accounts in parallel
      setLoadingAccounts(true)
      const fetchedAccounts: Account[] = []
      
      await Promise.all(
        allUniqueIds.map(async (id) => {
          try {
            const accRes = await fetch(`/accounts/${id}`)
            if (accRes.ok) {
              const acc: Account = await accRes.json()
              fetchedAccounts.push(acc)
            }
          } catch (e) {
            console.error(`Could not fetch account ${id}`, e)
          }
        })
      )
      
      setAccounts(fetchedAccounts.sort((a, b) => a.ownerName.localeCompare(b.ownerName)))
    } catch (err: any) {
      addToast('error', 'Sync Failed', err.message || 'An unexpected error occurred while loading data.')
    } finally {
      setLoadingAccounts(false)
      setLoadingLedger(false)
    }
  }

  // Create Account Handler
  const handleCreateAccount = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newAccountName.trim()) return
    
    setActionLoading('create_account')
    try {
      const res = await fetch('/accounts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ownerName: newAccountName,
          initialBalance: parseFloat(newAccountBalance) || 0,
          currency: newAccountCurrency
        })
      })

      if (!res.ok) {
        const errorText = await res.text()
        throw new Error(errorText || 'Failed to create account')
      }

      const createdAcc: Account = await res.json()
      
      // Update local storage registry
      const localAccountIds: string[] = JSON.parse(localStorage.getItem('payflow_account_ids') || '[]')
      localStorage.setItem('payflow_account_ids', JSON.stringify([...localAccountIds, createdAcc.id]))

      setAccounts(prev => [...prev, createdAcc].sort((a, b) => a.ownerName.localeCompare(b.ownerName)))
      addToast('success', 'Account Opened', `Successfully created account for ${createdAcc.ownerName}!`)
      setNewAccountName('')
      setNewAccountBalance('1000')
    } catch (err: any) {
      addToast('error', 'Creation Failed', err.message || 'Failed to open account.')
    } finally {
      setActionLoading(null)
    }
  }

  // --- Initiate a NEW transfer — generates a fresh random UUID as the idempotency key ---
  // The key is completely opaque: not derived from amount, accounts, description, or time.
  // If the user intentionally submits the same amounts again, a new UUID is generated
  // and it is treated as a separate, new payment.
  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!transferFrom || !transferTo || !transferAmount) {
      addToast('error', 'Form Error', 'Please select both sender, receiver and specify an amount.')
      return
    }
    if (transferFrom === transferTo) {
      addToast('error', 'Form Error', 'Sender and receiver accounts must be different.')
      return
    }

    const newKey = crypto.randomUUID()
    const parsedAmount = parseFloat(transferAmount)
    const merchant = transferMerchant.trim()
    const desc = transferDesc.trim()

    // Store for retry — the UUID must not change between attempts of the same intent
    setPendingIdempotencyKey(newKey)
    setLastTransferPayload({ from: transferFrom, to: transferTo, amount: parsedAmount, merchant, desc })
    succeededTxnIdsRef.current = new Set()

    setSimLogs([{
      id: 1,
      label: 'Primary Transfer Request',
      timestamp: new Date().toLocaleTimeString(),
      status: 'PENDING',
      details: `Generated UUID key: ...${newKey.slice(-12)}`
    }])
    setSimPanelOpen(true)

    await sendApiRequest(newKey, 1, transferFrom, transferTo, parsedAmount, merchant, desc)
  }

  // --- Retry the last transfer reusing the EXACT same idempotency key ---
  // Backend idempotency pre-check: if the first attempt SUCCEEDED, returns the same
  // transaction row without debiting again. If it FAILED (no DB row written), processes
  // the transfer normally — money moves exactly once either way.
  const handleRetryLastTransfer = async () => {
    if (!pendingIdempotencyKey || !lastTransferPayload) return
    const nextId = simLogs.length + 1
    const retryNumber = nextId - 1

    setSimLogs(prev => [...prev, {
      id: nextId,
      label: `Retry Attempt #${retryNumber}`,
      timestamp: new Date().toLocaleTimeString(),
      status: 'PENDING',
      details: `Reusing UUID key: ...${pendingIdempotencyKey.slice(-12)}`
    }])

    await sendApiRequest(
      pendingIdempotencyKey,
      nextId,
      lastTransferPayload.from,
      lastTransferPayload.to,
      lastTransferPayload.amount,
      lastTransferPayload.merchant,
      lastTransferPayload.desc
    )
  }

  // --- DEV: Arm the one-shot backend error for the next /transactions call ---
  const handleSimulateError = async () => {
    setSimulatingError(true)
    try {
      const res = await fetch('/dev/simulate-error', { method: 'POST' })
      if (res.ok) {
        addToast('info', 'Error Armed', 'Next transaction call will return 503 once. Click "Execute IMPS Transfer" then retry with the same key to see safe idempotency in action.')
      } else {
        addToast('error', 'Dev Error', 'Could not arm the backend error simulation.')
      }
    } catch {
      addToast('error', 'Dev Error', 'Backend unreachable.')
    } finally {
      setSimulatingError(false)
    }
  }

  // --- Execute the actual transaction request via Vite dev proxy to Spring Boot ---
  const sendApiRequest = async (
    key: string,
    logId: number,
    from: string,
    to: string,
    amount: number,
    merchant: string,
    desc: string
  ) => {
    try {
      const res = await fetch('/transactions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': key
        },
        body: JSON.stringify({
          fromAccount: from,
          toAccount: to,
          amount,
          description: desc || 'IMPS Transfer',
          merchant: merchant || 'HDFC NetBanking'
        })
      })

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}))
        throw new Error(errorData.message || 'Transaction rejected by core balance validation.')
      }

      const txnRes = await res.json()

      // Detect idempotent replay: backend returned the same transaction ID we already
      // recorded as succeeded in this session — money was NOT moved again.
      const isIdempotentReplay = succeededTxnIdsRef.current.has(txnRes.id)

      let status: 'SUCCESS' | 'IDEMPOTENT_BLOCK' = 'SUCCESS'
      let details = `Completed. Core ID: ...${txnRes.id.substring(txnRes.id.length - 8)}. Status: ${txnRes.status}`

      if (isIdempotentReplay) {
        status = 'IDEMPOTENT_BLOCK'
        details = `Idempotent replay — UUID key matched existing transaction. Re-served Core ID: ...${txnRes.id.substring(txnRes.id.length - 8)} without moving money again.`
        addToast('info', 'Idempotency Protected', 'Same UUID key returned existing transaction. No double-debit occurred.')
      } else {
        succeededTxnIdsRef.current.add(txnRes.id)
        addToast('success', 'IMPS Completed', `Transfer of ₹${amount.toFixed(2)} completed successfully.`)
        await loadData()
      }

      setSimLogs(prev => prev.map(log =>
        log.id === logId ? { ...log, status, details } : log
      ))

    } catch (err: any) {
      setSimLogs(prev => prev.map(log =>
        log.id === logId ? { ...log, status: 'FAILED', details: err.message || 'API request failed.' } : log
      ))
      addToast('error', 'Transfer Failed', err.message || 'Core validation failed. Retry with the same key — it is safe.')
    }
  }

  // --- Reset panel and stored transfer state for a completely new payment ---
  const handleNewTransfer = () => {
    setSimPanelOpen(false)
    setSimLogs([])
    setPendingIdempotencyKey(null)
    setLastTransferPayload(null)
    succeededTxnIdsRef.current = new Set()
    setTransferAmount('')
    setTransferDesc('')
    setTransferMerchant('')
  }

  // Copy helper
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(text)
    setTimeout(() => setCopiedId(null), 2000)
  }

  // Get Account Owner Name helper
  const getAccountOwnerName = (id: string) => {
    const acc = accounts.find(a => a.id === id)
    return acc ? acc.ownerName : `Account (...${id.substring(id.length - 6)})`
  }

  // Ledger Verification Handler
  const handleVerifyLedger = async () => {
    setVerifyingLedger(true)
    setVerification(null)
    try {
      const res = await fetch('/ledger/verify')
      if (!res.ok) throw new Error('Ledger verification endpoint returned an error')
      const result: VerificationResult = await res.json()
      setVerification(result)
      if (result.valid) {
        addToast('success', 'Audit Complete', 'All ledger cascading SHA-256 block hashes are authentic.')
      } else {
        addToast('error', 'Audit Warning', `Tampered block discovered at index ${result.offendingIndex}.`)
      }
    } catch (err: any) {
      addToast('error', 'Audit Failed', err.message || 'Failed to verify ledger integrity.')
    } finally {
      setVerifyingLedger(false)
    }
  }

  return (
    <div className="h-screen w-screen bg-slate-50 text-slate-800 flex flex-col font-sans overflow-hidden select-none">
      {/* HDFC Corporate Header (fixed height) */}
      <header className="bg-[#004C8F] text-white shadow-sm flex-shrink-0 z-40 relative">
        {/* Red Top Highlight Accent Line */}
        <div className="h-1 bg-[#E31E24] w-full"></div>
        
        <div className="max-w-7xl mx-auto px-6 py-3.5 flex flex-row justify-between items-center gap-4">
          <div className="flex items-center gap-3">
            <div className="bg-white px-2 py-1 rounded border border-blue-900/10 shrink-0">
              <span className="text-[#004C8F] font-black text-base tracking-tight">Payflow x HDFC BANK</span>
            </div>
            <div className="h-6 w-[1px] bg-blue-300/30"></div>
            <div>
              <h1 className="text-sm font-bold tracking-tight text-white flex items-center gap-1.5 leading-none">
                PayFlow Core Engine <span className="text-white font-semibold text-[8px] bg-[#E31E24] px-1.5 py-0.5 rounded">NETBANKING DEV</span>
              </h1>
              <p className="text-[10px] text-blue-100/70 mt-0.5 leading-none">Ledger Sandbox Panel</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 bg-blue-950/40 border border-blue-400/20 px-2.5 py-1 rounded text-[10px] font-semibold">
              <span className="relative flex h-1.5 w-1.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75 font-sans"></span>
                <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-emerald-450"></span>
              </span>
              <span className="text-emerald-300">CORE</span>
            </div>

            <button
              onClick={loadData}
              disabled={loadingAccounts || loadingLedger}
              className="p-1.5 text-blue-200 hover:text-white bg-blue-800/40 hover:bg-blue-800/80 disabled:opacity-50 rounded border border-blue-400/25 transition-colors cursor-pointer"
              title="Sync Data"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${(loadingAccounts || loadingLedger) ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>
      </header>

      {/* Floating Corner Toasts overlay (doesn't push UI elements) */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-3 w-80 pointer-events-none">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`p-3.5 rounded-xl border shadow-lg flex items-start gap-2.5 pointer-events-auto animate-in slide-in-from-bottom-3 duration-300 ${
              t.type === 'success' ? 'bg-emerald-55 border-emerald-200 text-emerald-950' :
              t.type === 'error' ? 'bg-red-50 border-red-200 text-red-950' :
              'bg-blue-50 border-blue-200 text-blue-950'
            }`}
          >
            {t.type === 'success' && <CheckCircle className="h-4.5 w-4.5 text-emerald-600 shrink-0 mt-0.5" />}
            {t.type === 'error' && <ShieldAlert className="h-4.5 w-4.5 text-red-600 shrink-0 mt-0.5" />}
            {t.type === 'info' && <Lock className="h-4.5 w-4.5 text-[#004C8F] shrink-0 mt-0.5" />}
            
            <div className="flex-1 min-w-0">
              <p className="text-xs font-bold leading-tight">{t.title}</p>
              <p className="text-[10px] text-slate-600 mt-0.5 leading-normal">{t.message}</p>
            </div>
            
            <button
              onClick={() => removeToast(t.id)}
              className="text-slate-400 hover:text-slate-700 cursor-pointer shrink-0"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>

      {/* Pure UUID Idempotency Request Log Panel (persistent, no countdown) */}
      {simPanelOpen && simLogs.length > 0 && (
        <div className="fixed bottom-6 left-6 z-50 w-[400px] bg-white border border-slate-200 shadow-2xl rounded-2xl overflow-hidden animate-in slide-in-from-bottom-5 duration-300">
          {/* Top HDFC blue bar */}
          <div className="h-1 bg-[#004C8F] w-full"></div>

          <div className="p-4">
            <div className="flex justify-between items-center mb-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-[#004C8F]" />
                <h3 className="font-bold text-xs text-slate-800">Idempotency Request Log</h3>
              </div>
              <span className="text-[9px] bg-blue-50 border border-blue-200 text-[#004C8F] px-2 py-0.5 rounded-full font-bold tracking-wide">
                UUID · PURE
              </span>
            </div>

            <div className="bg-blue-50 border border-blue-100 rounded-lg p-2 mb-2.5 text-[10px] text-blue-800 leading-normal space-y-1">
              <div className="flex items-center gap-1 font-bold">
                <Lock className="h-3 w-3 text-[#004C8F] shrink-0" />
                <span>Active Idempotency Key (random UUID):</span>
              </div>
              <code className="block font-mono text-[8px] bg-white p-1 rounded border border-blue-100 text-slate-700 select-all break-all">
                {pendingIdempotencyKey}
              </code>
            </div>

            {/* Request log entries */}
            <div className="space-y-1.5 max-h-[160px] overflow-y-auto mb-3 border-y border-slate-100 py-2">
              {simLogs.map((log) => (
                <div key={log.id} className="flex justify-between items-start gap-2 bg-slate-50 border border-slate-200 rounded p-1.5 text-[9.5px]">
                  <div className="space-y-0.5 flex-1 min-w-0">
                    <span className="font-bold text-slate-700">{log.label}</span>
                    <p className="text-[8.5px] text-slate-500 leading-normal">{log.details}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <span className={`text-[7.5px] font-bold px-1 py-0.5 rounded uppercase tracking-wider inline-flex items-center gap-0.5 ${
                      log.status === 'PENDING' ? 'bg-amber-100 text-amber-800' :
                      log.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-800' :
                      log.status === 'IDEMPOTENT_BLOCK' ? 'bg-blue-100 text-[#004C8F]' :
                      'bg-red-100 text-red-800'
                    }`}>
                      {log.status === 'PENDING' && <Loader2 className="h-2 w-2 animate-spin text-amber-600" />}
                      {log.status === 'SUCCESS' && <Check className="h-2 w-2 text-emerald-600" />}
                      {log.status === 'IDEMPOTENT_BLOCK' && <Lock className="h-2 w-2 text-[#004C8F]" />}
                      {log.status === 'FAILED' && <X className="h-2 w-2 text-red-600" />}
                      {log.status === 'IDEMPOTENT_BLOCK' ? 'IDEMPOTENT' : log.status}
                    </span>
                    <p className="text-[7px] text-slate-400 mt-0.5">{log.timestamp}</p>
                  </div>
                </div>
              ))}
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleRetryLastTransfer}
                disabled={!pendingIdempotencyKey}
                className="flex-1 py-1.5 bg-[#004C8F] hover:bg-[#003c73] disabled:opacity-40 disabled:cursor-not-allowed text-white text-[10px] font-bold rounded-lg flex items-center justify-center gap-1 cursor-pointer transition-colors"
              >
                <RefreshCw className="h-3 w-3" />
                Retry Same Key
              </button>
              <button
                type="button"
                onClick={handleNewTransfer}
                className="flex-1 py-1.5 bg-slate-100 hover:bg-slate-200 border border-slate-200 text-slate-700 text-[10px] font-bold rounded-lg cursor-pointer transition-colors"
              >
                Done / New Transfer
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Fitted Grid Workspace (fills remaining vertical screen space) */}
      <main className="flex-1 overflow-hidden p-6 gap-6 grid grid-cols-12 max-w-7xl w-full mx-auto">
        
        {/* Left Column: Accounts Panel (Span 4, fits height, flex layout) */}
        <section className="col-span-4 h-full flex flex-col gap-6 overflow-hidden">
          {/* Create Account Box (fixed height) */}
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-sm flex-shrink-0">
            <h2 className="text-xs font-bold text-slate-800 flex items-center gap-1.5 mb-3 border-b border-slate-100 pb-2">
              <PlusCircle className="h-4 w-4 text-[#004C8F]" />
              Open New Core Account
            </h2>

            <form onSubmit={handleCreateAccount} className="space-y-3">
              <div>
                <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Owner Name</label>
                <div className="relative">
                  <User className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  <input
                    type="text"
                    required
                    value={newAccountName}
                    onChange={(e) => setNewAccountName(e.target.value)}
                    placeholder="Enter full name"
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 pl-8 pr-2.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#004C8F] focus:bg-white transition-all font-sans"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Opening Balance</label>
                  <div className="relative">
                    <span className="absolute left-2.5 top-2 text-slate-400 text-xs font-bold">₹</span>
                    <input
                      type="number"
                      required
                      min="0"
                      value={newAccountBalance}
                      onChange={(e) => setNewAccountBalance(e.target.value)}
                      className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 pl-6 pr-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#004C8F] focus:bg-white transition-all font-sans"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Currency</label>
                  <select
                    value={newAccountCurrency}
                    onChange={(e) => setNewAccountCurrency(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 px-2 text-xs text-slate-800 focus:outline-none focus:border-[#004C8F] focus:bg-white transition-all cursor-pointer font-sans"
                  >
                    <option value="INR">INR (₹)</option>
                    <option value="USD">USD ($)</option>
                    <option value="EUR">EUR (€)</option>
                    <option value="GBP">GBP (£)</option>
                  </select>
                </div>
              </div>

              <button
                type="submit"
                disabled={actionLoading === 'create_account'}
                className="w-full py-2 bg-[#004C8F] hover:bg-[#003c73] disabled:opacity-50 text-white text-[11px] font-bold rounded-lg cursor-pointer transition-colors shadow-sm"
              >
                {actionLoading === 'create_account' ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin mx-auto text-white" />
                ) : (
                  <>Create Customer Account</>
                )}
              </button>
            </form>
          </div>

          {/* Account List Box (takes remaining space in column, scrollable list) */}
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-sm flex-1 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2.5 border-b border-slate-100 pb-2">
              <h2 className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                <Wallet className="h-4 w-4 text-[#004C8F]" />
                Customer Accounts ({accounts.length})
              </h2>
              {loadingAccounts && <Loader2 className="h-4 w-4 text-[#004C8F] animate-spin shrink-0" />}
            </div>

            {accounts.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-4 border border-dashed border-slate-200 rounded-xl bg-slate-50/50">
                <Wallet className="h-8 w-8 text-slate-300 mb-1.5" />
                <p className="text-xs text-slate-505 font-bold">No active bank accounts.</p>
                <p className="text-[9.5px] text-slate-400 max-w-[180px] mt-0.5 leading-normal">Open a new HDFC account to register vault nodes.</p>
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto space-y-2.5 pr-1.5">
                {accounts.map((acc) => (
                  <div
                    key={acc.id}
                    className="p-2.5 bg-slate-50 border border-slate-200 hover:border-slate-300 hover:bg-slate-100/50 rounded-xl transition-all group"
                  >
                    <div className="flex justify-between items-start gap-2">
                      <p className="text-xs font-bold text-slate-800 group-hover:text-[#004C8F] transition-colors truncate flex-1">
                        {acc.ownerName}
                      </p>
                      <span className="text-[8.5px] font-bold text-slate-500 bg-white border border-slate-200 px-1 py-0.5 rounded leading-none shrink-0 font-sans">
                        {acc.currency}
                      </span>
                    </div>

                    <div className="flex items-baseline justify-between mt-0.5">
                      <p className="text-[15px] font-black text-slate-900 tracking-tight">
                        {acc.balance.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </p>
                      <button
                        onClick={() => setTransferFrom(acc.id)}
                        className="text-[9px] font-bold text-[#004C8F] bg-blue-50 border border-blue-200 px-1.5 py-0.5 rounded hover:bg-blue-100 cursor-pointer transition-colors"
                      >
                        Set Sender
                      </button>
                    </div>

                    <div className="flex items-center gap-1.5 mt-2 pt-1.5 border-t border-slate-200/60">
                      <code className="text-[8.5px] text-slate-400 font-mono bg-transparent p-0 truncate flex-1 leading-none">
                        ACC: {acc.id}
                      </code>
                      <button
                        onClick={() => copyToClipboard(acc.id)}
                        className="text-slate-400 hover:text-slate-700 transition-colors cursor-pointer shrink-0"
                        title="Copy Account ID"
                      >
                        {copiedId === acc.id ? (
                          <span className="text-[8px] text-emerald-600 font-bold leading-none">Copied!</span>
                        ) : (
                          <Copy className="h-2.5 w-2.5" />
                        )}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* Middle Column: Initiate Transactions (Span 4, fits height, flex layout) */}
        <section className="col-span-4 h-full flex flex-col overflow-hidden">
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-sm flex flex-col h-full overflow-hidden">
            <h2 className="text-xs font-bold text-slate-800 flex items-center gap-1.5 mb-3 border-b border-slate-100 pb-2 flex-shrink-0">
              <TrendingUp className="h-4 w-4 text-[#004C8F]" />
              Initiate IMPS / NEFT Transfer
            </h2>

            <form onSubmit={handleTransfer} className="space-y-3.5 flex-1 flex flex-col min-h-0 overflow-hidden">
              <div className="flex-1 overflow-y-auto space-y-3.5 pr-1">
                <div>
                  <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">From (Debit Account)</label>
                  <select
                    required
                    value={transferFrom}
                    onChange={(e) => setTransferFrom(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 px-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#004C8F] transition-all cursor-pointer font-sans"
                  >
                    <option value="">-- Select Debit Account --</option>
                    {accounts.map(acc => (
                      <option key={`from-${acc.id}`} value={acc.id}>
                        {acc.ownerName} ({acc.balance.toFixed(2)} {acc.currency})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex justify-center -my-2 flex-shrink-0">
                  <div className="p-0.5 bg-slate-100 border border-slate-200 rounded-full shrink-0">
                    <ArrowRight className="h-3 w-3 text-slate-500 rotate-90" />
                  </div>
                </div>

                <div>
                  <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">To (Credit Account)</label>
                  <select
                    required
                    value={transferTo}
                    onChange={(e) => setTransferTo(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 px-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#004C8F] transition-all cursor-pointer font-sans"
                  >
                    <option value="">-- Select Credit Account --</option>
                    {accounts.map(acc => (
                      <option key={`to-${acc.id}`} value={acc.id}>
                        {acc.ownerName} ({acc.balance.toFixed(2)} {acc.currency})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Transaction Amount</label>
                  <div className="relative">
                    <span className="absolute left-2.5 top-1.5 text-slate-400 text-xs font-bold">₹</span>
                    <input
                      type="number"
                      required
                      min="0.01"
                      step="any"
                      value={transferAmount}
                      onChange={(e) => setTransferAmount(e.target.value)}
                      placeholder="0.00"
                      className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 pl-6 pr-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#004C8F] focus:bg-white transition-all font-sans"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  <div>
                    <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Merchant</label>
                    <input
                      type="text"
                      value={transferMerchant}
                      onChange={(e) => setTransferMerchant(e.target.value)}
                      placeholder="e.g. Netflix, Zara"
                      className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 px-2.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#004C8F] transition-all font-sans"
                    />
                  </div>

                  <div>
                    <label className="block text-[9.5px] font-bold text-slate-400 uppercase tracking-wider mb-0.5">Description</label>
                    <input
                      type="text"
                      value={transferDesc}
                      onChange={(e) => setTransferDesc(e.target.value)}
                      placeholder="e.g. Subscription"
                      className="w-full bg-slate-50 border border-slate-200 rounded-lg py-1.5 px-2.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#004C8F] transition-all font-sans"
                    />
                  </div>
                </div>

                {/* Pure UUID Idempotency explanation banner */}
                <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 flex items-start gap-2">
                  <Lock className="h-4 w-4 text-[#004C8F] shrink-0 mt-0.5" />
                  <div>
                    <p className="text-[10px] font-bold text-slate-700">Pure UUID Idempotency</p>
                    <p className="text-[9px] text-slate-500 mt-0.5 leading-normal">
                      Each new transfer generates a random UUID key — not derived from the payload. Retries reuse the same UUID; the backend returns the original result without debiting again.
                    </p>
                  </div>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-100 flex-shrink-0 space-y-2">
                <button
                  type="submit"
                  className="w-full py-2 bg-[#004C8F] hover:bg-[#003c73] text-white text-[11px] font-bold rounded-lg cursor-pointer flex items-center justify-center gap-1.5 transition-all shadow-sm"
                >
                  Execute IMPS Transfer
                  <ArrowRight className="h-3.5 w-3.5" />
                </button>

                {/* DEV-only controls */}
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleRetryLastTransfer}
                    disabled={!pendingIdempotencyKey || !lastTransferPayload}
                    className="flex-1 py-1.5 border border-slate-200 bg-slate-50 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed text-slate-600 text-[10px] font-bold rounded-lg cursor-pointer flex items-center justify-center gap-1 transition-colors"
                  >
                    <RefreshCw className="h-3 w-3" />
                    Retry Last Transfer
                  </button>
                  <button
                    type="button"
                    onClick={handleSimulateError}
                    disabled={simulatingError}
                    className="flex-1 py-1.5 border border-amber-200 bg-amber-50 hover:bg-amber-100 disabled:opacity-50 disabled:cursor-not-allowed text-amber-700 text-[10px] font-bold rounded-lg cursor-pointer flex items-center justify-center gap-1 transition-colors"
                  >
                    {simulatingError
                      ? <Loader2 className="h-3 w-3 animate-spin" />
                      : <ShieldAlert className="h-3 w-3" />}
                    Simulate Backend Error
                  </button>
                </div>
                <p className="text-[8.5px] text-slate-400 text-center leading-tight">
                  DEV · Simulate Error arms a one-shot 503 to demonstrate safe retry idempotency
                </p>
              </div>
            </form>
          </div>
        </section>

        {/* Right Column: Ledger History & Audit (Span 4, fits height, flex layout) */}
        <section className="col-span-4 h-full flex flex-col gap-6 overflow-hidden">
          {/* Audit Verification Block (fixed height) */}
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-sm flex-shrink-0">
            <h2 className="text-xs font-bold text-slate-800 flex items-center gap-1.5 mb-2.5 border-b border-slate-100 pb-2">
              <ShieldCheck className="h-4 w-4 text-[#004C8F]" />
              Core Cryptographic Audit
            </h2>

            <p className="text-[10.5px] text-slate-500 mb-3 leading-normal">
              Validate the cryptographic ledger integrity. The system re-hashes all historical blocks to detect any unauthorized database tampering.
            </p>

            <div className="space-y-3">
              <button
                onClick={handleVerifyLedger}
                disabled={verifyingLedger}
                className="w-full py-1.5 bg-slate-100 hover:bg-slate-200 border border-slate-200 text-slate-700 text-[11px] font-semibold rounded-lg flex items-center justify-center gap-1.5 cursor-pointer transition-all active:scale-95"
              >
                {verifyingLedger ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-[#004C8F]" />
                    Re-hashing Blockchain Ledger...
                  </>
                ) : (
                  <>
                    <ShieldCheck className="h-3.5 w-3.5 text-[#004C8F]" />
                    Verify Ledger Integrity
                  </>
                )}
              </button>

              {verification && (
                <div className={`p-3 border rounded-xl animate-in fade-in zoom-in-95 duration-300 ${
                  verification.valid 
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-950' 
                    : 'bg-red-50 border-red-200 text-red-950'
                }`}>
                  <div className="flex gap-2">
                    {verification.valid ? (
                      <ShieldCheck className="h-4 w-4 text-emerald-600 shrink-0 mt-0.5" />
                    ) : (
                      <ShieldAlert className="h-4 w-4 text-[#E31E24] shrink-0 mt-0.5" />
                    )}
                    <div>
                      <p className="text-[9.5px] font-bold uppercase tracking-wider">
                        {verification.valid ? 'Cryptographic Check Passed' : 'Ledger Altered'}
                      </p>
                      <p className="text-[9px] text-slate-655 mt-0.5 leading-normal">
                        {verification.valid 
                          ? 'Cascading SHA-256 validation complete. All ledger entries are authentic.' 
                          : `Tamper warning: Hash chain mismatch at entry index ${verification.offendingIndex}. ${verification.error}`
                        }
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Ledger Timeline Box (takes remaining space, scrollable list) */}
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-sm flex-1 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2.5 border-b border-slate-100 pb-2">
              <h2 className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                <ListRestart className="h-4 w-4 text-[#004C8F]" />
                Ledger Logs ({ledger.length})
              </h2>
              {loadingLedger && <Loader2 className="h-4 w-4 text-[#004C8F] animate-spin shrink-0" />}
            </div>

            {ledger.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-4 border border-dashed border-slate-200 rounded-xl bg-slate-50/50">
                <ListRestart className="h-8 w-8 text-slate-300 mb-1.5" />
                <p className="text-xs text-slate-505 font-bold">Ledger timeline is empty.</p>
                <p className="text-[9.5px] text-slate-400 max-w-[180px] mt-0.5 leading-normal">Execute a transaction to generate immutable ledger blocks.</p>
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto space-y-3.5 pr-1.5 relative">
                {/* Vertical timeline line */}
                <div className="absolute left-[11px] top-1.5 bottom-1.5 w-[1px] bg-slate-200 -z-10"></div>

                {ledger.map((entry) => (
                  <div key={entry.id} className="relative flex gap-2.5 text-left">
                    {/* Index Bullet */}
                    <div className="h-5 w-5 rounded-full bg-white border border-slate-200 flex items-center justify-center text-[8.5px] font-black text-[#004C8F] shrink-0 select-none">
                      {entry.chainIndex}
                    </div>

                    <div className="flex-1 bg-slate-55/40 border border-slate-250 rounded-xl p-2.5 hover:border-slate-300 transition-colors">
                      <div className="flex justify-between items-start gap-1.5">
                        <div className="min-w-0 flex-1">
                          <p className="text-[11px] font-bold text-slate-850 truncate">
                            {getAccountOwnerName(entry.fromAccount)} → {getAccountOwnerName(entry.toAccount)}
                          </p>
                          <p className="text-[8.5px] text-slate-400 mt-0.5 leading-none truncate">
                            {new Date(entry.recordedAt).toLocaleTimeString()} · Tx: ...{entry.transactionId.substring(entry.transactionId.length - 6)}
                          </p>
                        </div>
                        <p className="text-[11px] font-black text-[#004C8F] shrink-0">
                          {entry.amount.toFixed(2)} {entry.currency}
                        </p>
                      </div>

                      {/* Optional metadata info block */}
                      {(entry.merchant || entry.description) && (
                        <div className="text-[8.5px] text-slate-500 bg-slate-100/50 border border-slate-200/50 rounded-lg p-1.5 mt-1.5 space-y-0.5">
                          {entry.merchant && (
                            <p className="font-semibold">
                              Merchant: <span className="text-[#004C8F]">{entry.merchant}</span>
                            </p>
                          )}
                          {entry.description && (
                            <p className="text-slate-450 leading-tight">
                              Note: "{entry.description}"
                            </p>
                          )}
                        </div>
                      )}

                      {/* AI Classification Footer */}
                      <div className="flex items-center justify-between gap-1.5 mt-2 pt-2 border-t border-slate-200/60 flex-wrap">
                        <div className="flex items-center gap-0.5">
                          <Tag className="h-2.5 w-2.5 text-slate-400" />
                          <span className="text-[8px] font-black text-[#004C8F] bg-blue-50 border border-blue-150 px-1 py-0.5 rounded uppercase tracking-wide">
                            {entry.category || 'PENDING'}
                          </span>
                        </div>
                        
                        <span className="text-[8px] text-slate-400 font-mono select-all truncate max-w-[80px]" title={entry.entryHash}>
                          {entry.entryHash.substring(0, 8)}...
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

      </main>

      {/* Footer (fixed height) */}
      <footer className="border-t border-slate-200 bg-slate-100 py-2.5 text-center flex-shrink-0 z-10">
        <p className="text-[9.5px] text-slate-400 font-mono">
          HDFC Bank Core Microservices Sandbox Console · Protected Mode
        </p>
      </footer>
    </div>
  )
}
