'use client';

import { AlertCircle } from 'lucide-react';

interface Props {
  message: string;
  onRetry: () => void;
}

export default function ErrorBanner({ message, onRetry }: Props) {
  return (
    <div className="flex flex-col items-center justify-center text-center px-6 py-16 bg-app-bg">
      <AlertCircle className="w-10 h-10 text-app-error" />
      <h3 className="mt-4 text-base font-semibold text-app-text">
        Couldn&rsquo;t load the list
      </h3>
      <p className="mt-1 text-sm text-app-text-secondary max-w-sm">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-5 px-5 py-2.5 rounded-lg bg-primary-600 text-white text-sm font-semibold hover:bg-primary-700"
      >
        Try again
      </button>
    </div>
  );
}
