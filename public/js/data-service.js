/**
 * Data Service & Sync Adapter for Cricket Scorecard Overlay
 * Handles API polling, BroadcastChannel real-time sync, and offline resilience.
 */
class DataService {
  constructor(options = {}) {
    this.pollInterval = options.pollInterval || 5000;
    this.apiUrl = options.apiUrl || '/api';
    this.onUpdateCallbacks = [];
    this.onErrorCallbacks = [];
    this.isPolling = false;
    this.pollTimer = null;
    this.lastSuccessfulSync = Date.now();
    this.currentState = null;
    this.staleThresholdMs = 15000; // 15 seconds

    // Real-time inter-tab sync channel
    try {
      this.channel = new BroadcastChannel('ckt_score_sync');
      this.channel.onmessage = (event) => {
        if (event.data && event.data.type === 'STATE_UPDATE') {
          this.handleStateUpdate(event.data.payload, 'broadcast_channel');
        } else if (event.data && event.data.type === 'EVENT_TRIGGER') {
          if (this.onEventTriggerCallback) {
            this.onEventTriggerCallback(event.data.payload);
          }
        }
      };
    } catch (e) {
      console.warn("BroadcastChannel not available, using polling fallback", e);
      this.channel = null;
    }

    // Listen to localStorage storage events for cross-frame/window sync
    window.addEventListener('storage', (e) => {
      if (e.key === 'ckt_match_state' && e.newValue) {
        try {
          const parsed = JSON.parse(e.newValue);
          this.handleStateUpdate(parsed, 'local_storage');
        } catch (err) {}
      }
    });
  }

  onUpdate(callback) {
    this.onUpdateCallbacks.push(callback);
  }

  onError(callback) {
    this.onErrorCallbacks.push(callback);
  }

  onEvent(callback) {
    this.onEventTriggerCallback = callback;
  }

  startPolling() {
    if (this.isPolling) return;
    this.isPolling = true;
    this.poll();
  }

  stopPolling() {
    this.isPolling = false;
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }

  async poll() {
    if (!this.isPolling) return;
    try {
      const data = await this.fetchScore();
      this.handleStateUpdate(data, 'http_poll');
    } catch (err) {
      this.handleError(err);
    } finally {
      if (this.isPolling) {
        const interval = (this.currentState && this.currentState.pollingInterval) 
          ? this.currentState.pollingInterval * 1000 
          : this.pollInterval;
        this.pollTimer = setTimeout(() => this.poll(), interval);
      }
    }
  }

  handleStateUpdate(newState, source = 'unknown') {
    this.lastSuccessfulSync = Date.now();
    newState.isStale = false;
    this.currentState = newState;

    // Cache locally
    try {
      localStorage.setItem('ckt_match_state', JSON.stringify(newState));
    } catch (e) {}

    // Notify listeners
    this.onUpdateCallbacks.forEach(cb => {
      try { cb(newState, source); } catch (e) { console.error("Error in update callback", e); }
    });
  }

  handleError(err) {
    console.warn("Sync error:", err.message);
    const timeSinceLastSync = Date.now() - this.lastSuccessfulSync;
    
    if (this.currentState && timeSinceLastSync > this.staleThresholdMs) {
      this.currentState.isStale = true;
      this.onUpdateCallbacks.forEach(cb => cb(this.currentState, 'stale_warning'));
    }

    this.onErrorCallbacks.forEach(cb => {
      try { cb(err); } catch (e) {}
    });
  }

  broadcast(type, payload) {
    if (this.channel) {
      this.channel.postMessage({ type, payload });
    }
    if (type === 'STATE_UPDATE') {
      try {
        localStorage.setItem('ckt_match_state', JSON.stringify(payload));
      } catch (e) {}
    }
  }

  async autoConfigureFromUrl(url) {
    const res = await fetch(`${this.apiUrl}/auto-configure`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url })
    });
    const result = await res.json();
    if (result.success && result.state) {
      this.handleStateUpdate(result.state, 'auto_configure');
      this.broadcast('STATE_UPDATE', result.state);
    }
    return result;
  }

  async fetchScore() {
    const res = await fetch(`${this.apiUrl}/score?t=${Date.now()}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  }

  async updateConfig(config) {
    const res = await fetch(`${this.apiUrl}/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    const result = await res.json();
    if (result.success && result.state) {
      this.handleStateUpdate(result.state, 'user_action');
      this.broadcast('STATE_UPDATE', result.state);
    }
    return result;
  }

  async updateOverlaySettings(settings) {
    const res = await fetch(`${this.apiUrl}/overlay-settings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings)
    });
    const result = await res.json();
    if (result.success && this.currentState) {
      this.currentState.overlaySettings = { ...this.currentState.overlaySettings, ...settings };
      this.handleStateUpdate(this.currentState, 'user_action');
      this.broadcast('STATE_UPDATE', this.currentState);
    }
    return result;
  }

  async triggerEvent(type, message) {
    const res = await fetch(`${this.apiUrl}/trigger-event`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type, message })
    });
    const result = await res.json();
    if (result.success) {
      this.broadcast('EVENT_TRIGGER', { type, message, timestamp: Date.now() });
    }
    return result;
  }

  async recordBall(outcome, runs = 0) {
    const res = await fetch(`${this.apiUrl}/ball`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ outcome, runs })
    });
    const result = await res.json();
    if (result.success && result.state) {
      this.handleStateUpdate(result.state, 'user_action');
      this.broadcast('STATE_UPDATE', result.state);
    }
    return result;
  }

  async undo() {
    const res = await fetch(`${this.apiUrl}/undo`, {
      method: 'POST'
    });
    const result = await res.json();
    if (result.success && result.state) {
      this.handleStateUpdate(result.state, 'user_action');
      this.broadcast('STATE_UPDATE', result.state);
    }
    return result;
  }

  async resetReference() {
    const res = await fetch(`${this.apiUrl}/reset-reference`, {
      method: 'POST'
    });
    const result = await res.json();
    if (result.success && result.state) {
      this.handleStateUpdate(result.state, 'user_action');
      this.broadcast('STATE_UPDATE', result.state);
    }
    return result;
  }
}

window.dataService = new DataService();
