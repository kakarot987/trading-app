import { type HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'success' | 'warning' | 'destructive' | 'outline';
}

export function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        variant === 'default' && 'bg-[hsl(var(--primary))]/10 text-[hsl(var(--primary))]',
        variant === 'success' && 'bg-green-500/10 text-green-400',
        variant === 'warning' && 'bg-yellow-500/10 text-yellow-400',
        variant === 'destructive' && 'bg-red-500/10 text-red-400',
        variant === 'outline' && 'border border-[hsl(var(--border))] text-[hsl(var(--muted-foreground))]',
        className
      )}
      {...props}
    />
  );
}