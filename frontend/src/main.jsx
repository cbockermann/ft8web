import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { FeedProvider } from './feed/FeedProvider.jsx';
import { contextPath } from './api.js';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={contextPath}>
      <FeedProvider>
        <App />
      </FeedProvider>
    </BrowserRouter>
  </React.StrictMode>
);
