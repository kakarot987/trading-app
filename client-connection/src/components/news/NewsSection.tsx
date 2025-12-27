// src/components/news/NewsSection.tsx
import { useState, useEffect } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { Badge } from '@/components/ui/Badge';
import { Newspaper, ExternalLink } from 'lucide-react';

interface NewsItem {
  id: string;
  title: string;
  source: string;
  timestamp: number;
  url?: string;
  category?: 'CRYPTO' | 'STOCK' | 'FOREX' | 'GENERAL';
  sentiment?: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
}

interface NewsSectionProps {
  maxItems?: number;
}

export function NewsSection({ maxItems = 10 }: NewsSectionProps) {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // TODO: Connect to your Service 5 (News Service)
  useEffect(() => {
    // Mock data for now - replace with actual API call
    const mockNews: NewsItem[] = [
      {
        id: '1',
        title: 'Bitcoin reaches new all-time high above $100K',
        source: 'CryptoNews',
        timestamp: Date.now() - 5 * 60 * 1000,
        category: 'CRYPTO',
        sentiment: 'POSITIVE',
      },
      {
        id: '2',
        title: 'Federal Reserve keeps interest rates unchanged',
        source: 'Bloomberg',
        timestamp: Date.now() - 15 * 60 * 1000,
        category: 'GENERAL',
        sentiment: 'NEUTRAL',
      },
      {
        id: '3',
        title: 'Tesla announces record Q4 deliveries',
        source: 'Reuters',
        timestamp: Date.now() - 30 * 60 * 1000,
        category: 'STOCK',
        sentiment: 'POSITIVE',
      },
      {
        id: '4',
        title: 'Oil prices surge amid supply concerns',
        source: 'CNBC',
        timestamp: Date.now() - 45 * 60 * 1000,
        category: 'FOREX',
        sentiment: 'NEGATIVE',
      },
      {
        id: '5',
        title: 'Gold hits 6-month low on strong dollar',
        source: 'MarketWatch',
        timestamp: Date.now() - 60 * 60 * 1000,
        category: 'FOREX',
        sentiment: 'NEGATIVE',
      },
    ];

    setNews(mockNews);
    setIsLoading(false);
  }, []);

  const formatTimeAgo = (timestamp: number) => {
    const seconds = Math.floor((Date.now() - timestamp) / 1000);
    if (seconds < 60) return `${seconds}s ago`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    return `${Math.floor(seconds / 86400)}d ago`;
  };

  const getCategoryColor = (category?: string) => {
    switch (category) {
      case 'CRYPTO':
        return 'bg-purple-500/20 text-purple-400 border-purple-500/30';
      case 'STOCK':
        return 'bg-blue-500/20 text-blue-400 border-blue-500/30';
      case 'FOREX':
        return 'bg-green-500/20 text-green-400 border-green-500/30';
      default:
        return 'bg-slate-500/20 text-slate-400 border-slate-500/30';
    }
  };

  const getSentimentColor = (sentiment?: string) => {
    switch (sentiment) {
      case 'POSITIVE':
        return 'text-green-500';
      case 'NEGATIVE':
        return 'text-red-500';
      default:
        return 'text-slate-400';
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Newspaper className="w-5 h-5 text-blue-500" />
          Market News
        </CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="text-center py-8 text-slate-400">
            Loading news...
          </div>
        ) : (
          <div className="space-y-3">
            {news.slice(0, maxItems).map((item) => (
              <div
                key={item.id}
                className="p-3 rounded-lg border border-slate-700 hover:border-slate-600 hover:bg-slate-800 transition-colors group"
              >
                {/* Header */}
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex-1">
                    <h4 className="text-white font-medium leading-tight mb-1 group-hover:text-blue-400 transition-colors">
                      {item.title}
                    </h4>
                    <div className="flex items-center gap-2 text-xs text-slate-400">
                      <span>{item.source}</span>
                      <span>•</span>
                      <span>{formatTimeAgo(item.timestamp)}</span>
                    </div>
                  </div>
                  <ExternalLink className="w-4 h-4 text-slate-500 group-hover:text-blue-400 transition-colors flex-shrink-0" />
                </div>

                {/* Tags */}
                <div className="flex items-center gap-2">
                  {item.category && (
                    <Badge
                      className={`text-xs ${getCategoryColor(item.category)}`}
                    >
                      {item.category}
                    </Badge>
                  )}
                  {item.sentiment && (
                    <span
                      className={`text-xs font-medium ${getSentimentColor(
                        item.sentiment
                      )}`}
                    >
                      {item.sentiment === 'POSITIVE' && '📈'}
                      {item.sentiment === 'NEGATIVE' && '📉'}
                      {item.sentiment === 'NEUTRAL' && '➡️'}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TODO: Add Service 5 connection note */}
        <div className="mt-4 p-3 bg-slate-800 rounded-lg text-xs text-slate-400">
          <div className="font-medium mb-1">🔌 Service Integration</div>
          <div>
            Connect to your Service 5 (News Service) to get real-time market
            news. Replace mock data in NewsSection.tsx with API calls.
          </div>
        </div>
      </CardContent>
    </Card>
  );
}