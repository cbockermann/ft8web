import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { FeedProvider } from './feed/FeedProvider.jsx';
import { ControlProvider } from './control/ControlProvider.jsx';
import { WorkedCallsProvider } from './qsolog/WorkedCallsProvider.jsx';
import { contextPath } from './api.js';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={contextPath}>
      <WorkedCallsProvider>
        <FeedProvider>
          <ControlProvider>
            <App />
          </ControlProvider>
        </FeedProvider>
      </WorkedCallsProvider>
    </BrowserRouter>
  </React.StrictMode>
);
