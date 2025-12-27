// src/lib/api-client.ts
import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { config } from '@/config';

export interface ApiError {
  error: string;
  timestamp: number;
  status?: number;
}

class ApiClient {
  private client: AxiosInstance;

  constructor(baseURL: string) {
    this.client = axios.create({
      baseURL,
      timeout: config.api.timeout,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors() {
    // Request interceptor
    this.client.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        // Add timestamp to requests for debugging
        config.metadata = { startTime: Date.now() };
        
        // Future: Add JWT token here
        // const token = localStorage.getItem('auth_token');
        // if (token) {
        //   config.headers.Authorization = `Bearer ${token}`;
        // }

        console.log(`🚀 API Request: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        console.error('❌ Request Error:', error);
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.client.interceptors.response.use(
      (response) => {
        const duration = Date.now() - (response.config.metadata?.startTime || 0);
        console.log(
          `✅ API Response: ${response.config.url} (${duration}ms)`,
          response.data
        );
        return response;
      },
      (error: AxiosError<ApiError>) => {
        const duration = Date.now() - (error.config?.metadata?.startTime || 0);
        
        if (error.response) {
          // Server responded with error status
          console.error(
            `❌ API Error: ${error.config?.url} (${duration}ms)`,
            error.response.status,
            error.response.data
          );

          // Handle specific error codes
          switch (error.response.status) {
            case 401:
              // Future: Trigger re-authentication
              console.warn('Unauthorized - Auth required');
              break;
            case 429:
              console.warn('Rate limit exceeded');
              break;
            case 503:
              console.warn('Service unavailable');
              break;
          }
        } else if (error.request) {
          // Request made but no response
          console.error('❌ No response from server:', error.message);
        } else {
          // Request setup error
          console.error('❌ Request setup error:', error.message);
        }

        return Promise.reject(error);
      }
    );
  }

  public get instance() {
    return this.client;
  }
}

// Create API client instance
export const apiClient = new ApiClient(config.api.baseUrl);

// Extend AxiosRequestConfig to include metadata
declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    metadata?: {
      startTime: number;
    };
  }
}