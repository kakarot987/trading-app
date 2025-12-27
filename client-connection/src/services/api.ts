import axios from 'axios';
import { config } from '@/config';

// ============================================
// Axios Instance Configuration
// ============================================

export const apiClient = axios.create({
  baseURL: config.api.baseUrl,
  timeout: config.api.timeout,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
apiClient.interceptors.request.use(
  (config) => {
    // Add JWT token here when auth is implemented
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle global errors
    if (error.response?.status === 401) {
      // Handle unauthorized - redirect to login when auth is implemented
      console.error('Unauthorized access');
    }
    
    if (error.response?.status === 429) {
      console.error('Rate limit exceeded');
    }

    return Promise.reject(error);
  }
);