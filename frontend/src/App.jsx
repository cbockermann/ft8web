import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useFeed } from './feed/FeedProvider.jsx';
import FeedView from './views/FeedView.jsx';
import OverviewView from './views/OverviewView.jsx';
import ConfigView from './views/ConfigView.jsx';

export default function App() {
  const { connected } = useFeed();
  return (
    <div className="app">
      <header>
        <h1>FT8 Monitor</h1>
        <nav className="nav">
          <NavLink to="/overview">Overview</NavLink>
          <NavLink to="/feed">Feed</NavLink>
          <NavLink to="/config">Config</NavLink>
        </nav>
        <span className={connected ? 'status ok' : 'status down'}>
          {connected ? 'live' : 'disconnected'}
        </span>
      </header>

      <Routes>
        <Route path="/" element={<Navigate to="/overview" replace />} />
        <Route path="/overview" element={<OverviewView />} />
        <Route path="/feed" element={<FeedView />} />
        <Route path="/config" element={<ConfigView />} />
        <Route path="*" element={<Navigate to="/overview" replace />} />
      </Routes>
    </div>
  );
}
