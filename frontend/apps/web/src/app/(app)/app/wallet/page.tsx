'use client';

import { Suspense, useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, CreditCard } from 'lucide-react';
import { getAuthToken } from '@pantopus/api';
import WalletBalanceCard from '@/components/wallet/WalletBalanceCard';
import WalletTransactionList from '@/components/wallet/WalletTransactionList';
import WithdrawModal from '@/components/wallet/WithdrawModal';

function WalletContent() {
  const router = useRouter();
  const [showWithdraw, setShowWithdraw] = useState(false);
  const [walletBalance, setWalletBalance] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => { if (!getAuthToken()) router.push('/login'); }, [router]);

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button onClick={() => router.back()} className="p-1.5 hover:bg-app-hover rounded-lg transition">
            <ArrowLeft className="w-5 h-5 text-app-text" />
          </button>
          <h1 className="text-xl font-bold text-app-text">Wallet</h1>
        </div>
        <button onClick={() => router.push('/app/settings/payments')}
          className="flex items-center gap-1.5 text-sm text-emerald-600 font-medium hover:text-emerald-700">
          <CreditCard className="w-4 h-4" /> Payment Settings
        </button>
      </div>

      {/* Balance card */}
      <div className="mb-6">
        <WalletBalanceCard
          key={refreshKey}
          showActions
          onLoad={(wallet) => setWalletBalance(wallet.balance)}
          onWithdraw={() => setShowWithdraw(true)}
        />
      </div>

      {/* Transaction history */}
      <div>
        <h2 className="text-sm font-bold text-app-text-strong mb-3">Transaction History</h2>
        <WalletTransactionList key={refreshKey} />
      </div>

      {/* Withdraw modal */}
      {showWithdraw && (
        <WithdrawModal
          balance={walletBalance}
          onClose={() => setShowWithdraw(false)}
          onSuccess={() => {
            setShowWithdraw(false);
            setRefreshKey((k) => k + 1);
          }}
        />
      )}
    </div>
  );
}

export default function WalletPage() { return <Suspense><WalletContent /></Suspense>; }
