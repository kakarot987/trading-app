import { useState, type ReactNode } from 'react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import type { WebSocketStatus } from '@/types';

interface MainLayoutProps {
  children: ReactNode;
  tradeWsStatus: WebSocketStatus;
  alertWsStatus: WebSocketStatus;
  activeView: string;
  onViewChange: (view: string) => void;
}

export function MainLayout({
  children,
  tradeWsStatus,
  alertWsStatus,
  activeView,
  onViewChange,
}: MainLayoutProps) {
  return (
    <div className="min-h-screen bg-[hsl(var(--background))]">
      <Header tradeWsStatus={tradeWsStatus} alertWsStatus={alertWsStatus} />
      <Sidebar activeView={activeView} onViewChange={onViewChange} />
      
      {/* Main Content Area */}
      <main className="ml-16 min-h-[calc(100vh-3.5rem)] p-4">
        {children}
      </main>
    </div>
  );
}