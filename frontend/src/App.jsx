import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useFeed } from './feed/FeedProvider.jsx';
import FeedView from './views/FeedView.jsx';
import OverviewView from './views/OverviewView.jsx';
import StatsView from './views/StatsView.jsx';
import ReportsView from './views/ReportsView.jsx';
import ControlView from './views/ControlView.jsx';
import ConfigView from './views/ConfigView.jsx';

export default function App() {
  const { connected } = useFeed();
  return (
    <div className="app">
      <header>
        <h1>FT8 Monitor</h1>
        <nav className="nav">
          <NavLink to="/" end>Overview</NavLink>
          <NavLink to="/feed">Feed</NavLink>
          <NavLink to="/stats">Stats</NavLink>
          <NavLink to="/reports">Reports</NavLink>
          <NavLink to="/control">Control</NavLink>
          <NavLink to="/config">Config</NavLink>
        </nav>
        <span className={connected ? 'status ok' : 'status down'}>
          {connected ? 'live' : 'disconnected'}
        </span>
      </header>

      <Routes>
        {/* Overview is the entry page: rendered directly at "/" (no redirect). */}
        <Route path="/" element={<OverviewView />} />
        <Route path="/overview" element={<Navigate to="/" replace />} />
        <Route path="/feed" element={<FeedView />} />
        <Route path="/stats" element={<StatsView />} />
        <Route path="/reports" element={<ReportsView />} />
        <Route path="/control" element={<ControlView />} />
        <Route path="/config" element={<ConfigView />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
