import { useEffect, useState } from 'react';

/** A wall-clock snapshot updated outside React's render phase. */
export function useClock(refreshMs = 30_000): number {
  const [now, setNow] = useState(0);

  useEffect(() => {
    const update = () => setNow(Date.now());
    const initial = setTimeout(update, 0);
    const timer = setInterval(update, refreshMs);
    return () => {
      clearTimeout(initial);
      clearInterval(timer);
    };
  }, [refreshMs]);

  return now;
}
