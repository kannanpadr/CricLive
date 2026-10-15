/**
 * Operator Control Panel Controller
 * Handles match settings, CricHeroes URL auto-config, manual scoring, and live preview.
 */
document.addEventListener('DOMContentLoaded', () => {
  // Elements
  const previewIframe = document.getElementById('preview-iframe');
  const previewContainer = document.getElementById('preview-container');
  const previewBg = document.getElementById('preview-bg');
  
  // Connection / Status
  const statusBadge = document.getElementById('sync-status-badge');
  const statusText = document.getElementById('sync-status-text');
  const lastSyncTime = document.getElementById('last-sync-time');

  // URL Auto-Configure Elements
  const inputMatchUrl = document.getElementById('cfg-match-url');
  const btnAutoConfigureUrl = document.getElementById('btn-auto-configure-url');
  const urlSyncStatusMsg = document.getElementById('url-sync-status-msg');

  // Config inputs
  const inputMatchId = document.getElementById('cfg-match-id');
  const inputTournament = document.getElementById('cfg-tournament');
  const inputTeamAName = document.getElementById('cfg-team-a-name');
  const inputTeamAShort = document.getElementById('cfg-team-a-short');
  const inputTeamBName = document.getElementById('cfg-team-b-name');
  const inputTeamBShort = document.getElementById('cfg-team-b-short');
  const inputPollInterval = document.getElementById('cfg-poll-interval');
  const btnSaveConfig = document.getElementById('btn-save-config');
  const btnResetReference = document.getElementById('btn-reset-reference');

  // Overlay Settings
  const toggleOverlayVisible = document.getElementById('toggle-overlay-visible');
  const selectLayout = document.getElementById('select-layout');
  const selectTheme = document.getElementById('select-theme');
  const toggleSponsors = document.getElementById('toggle-sponsors');
  const inputSponsorText = document.getElementById('cfg-sponsor-text');
  const toggleSimulation = document.getElementById('toggle-simulation');
  const toggleSound = document.getElementById('toggle-sound');

  // OBS URL & Copy
  const obsUrlText = document.getElementById('obs-url-text');
  const btnCopyObsUrl = document.getElementById('btn-copy-obs-url');

  const overlayUrl = `${window.location.origin}/overlay.html`;
  if (obsUrlText) obsUrlText.textContent = overlayUrl;

  if (btnCopyObsUrl) {
    btnCopyObsUrl.addEventListener('click', () => {
      navigator.clipboard.writeText(overlayUrl);
      const original = btnCopyObsUrl.textContent;
      btnCopyObsUrl.textContent = "COPIED! ✓";
      setTimeout(() => { btnCopyObsUrl.textContent = original; }, 2000);
    });
  }

  // Adjust preview iframe scaling to fit 16:9 box
  function adjustPreviewScale() {
    if (!previewContainer || !previewIframe) return;
    const containerWidth = previewContainer.clientWidth;
    const scale = containerWidth / 1920;
    previewIframe.style.transform = `scale(${scale})`;
  }

  window.addEventListener('resize', adjustPreviewScale);
  setTimeout(adjustPreviewScale, 150);

  // Sync Form with State
  function populateForm(state) {
    if (!state) return;

    if (inputMatchId) inputMatchId.value = state.matchId || '';
    if (inputMatchUrl && !inputMatchUrl.matches(':focus')) {
      inputMatchUrl.value = state.matchUrl || '';
    }
    if (inputTournament) inputTournament.value = state.tournament || '';
    if (inputPollInterval) inputPollInterval.value = state.pollingInterval || 7;

    if (state.teamA) {
      if (inputTeamAName) inputTeamAName.value = state.teamA.name || '';
      if (inputTeamAShort) inputTeamAShort.value = state.teamA.shortName || '';
    }
    if (state.teamB) {
      if (inputTeamBName) inputTeamBName.value = state.teamB.name || '';
      if (inputTeamBShort) inputTeamBShort.value = state.teamB.shortName || '';
    }

    const settings = state.overlaySettings || {};
    if (toggleOverlayVisible) toggleOverlayVisible.checked = settings.visible !== false;
    if (selectLayout && settings.layout) selectLayout.value = settings.layout;
    if (selectTheme && settings.theme) selectTheme.value = settings.theme;
    if (toggleSponsors) toggleSponsors.checked = settings.showSponsors !== false;
    if (inputSponsorText) inputSponsorText.value = settings.sponsorText || '';

    // Update connection badge
    if (statusBadge && statusText) {
      if (state.isStale) {
        statusBadge.className = 'status-badge warning';
        statusText.textContent = 'DATA DELAYED';
      } else {
        statusBadge.className = 'status-badge';
        statusText.textContent = 'LIVE & SYNCED';
      }
    }
    if (lastSyncTime) {
      lastSyncTime.textContent = new Date().toLocaleTimeString();
    }
  }

  // Auto-Configure from CricHeroes Full URL Handler
  if (btnAutoConfigureUrl) {
    btnAutoConfigureUrl.addEventListener('click', async () => {
      const url = inputMatchUrl.value.trim();
      if (!url) {
        alert("Please paste a CricHeroes URL first!");
        return;
      }

      btnAutoConfigureUrl.disabled = true;
      btnAutoConfigureUrl.textContent = "⏳ Fetching CricHeroes Scorecard...";
      if (urlSyncStatusMsg) {
        urlSyncStatusMsg.style.display = 'block';
        urlSyncStatusMsg.style.backgroundColor = 'rgba(6, 182, 212, 0.15)';
        urlSyncStatusMsg.style.color = '#38bdf8';
        urlSyncStatusMsg.style.border = '1px solid rgba(6, 182, 212, 0.4)';
        urlSyncStatusMsg.textContent = "Connecting to CricHeroes and parsing match details...";
      }

      try {
        const res = await window.dataService.autoConfigureFromUrl(url);
        if (res.success && res.state) {
          populateForm(res.state);
          if (urlSyncStatusMsg) {
            urlSyncStatusMsg.style.backgroundColor = 'rgba(16, 185, 129, 0.15)';
            urlSyncStatusMsg.style.color = '#34d399';
            urlSyncStatusMsg.style.border = '1px solid rgba(16, 185, 129, 0.4)';
            urlSyncStatusMsg.textContent = `✓ Auto-Configured! ${res.state.tournament} (${res.state.teamA.shortName} vs ${res.state.teamB.shortName})`;
          }
        } else {
          throw new Error(res.error || "Could not auto-configure");
        }
      } catch (err) {
        if (urlSyncStatusMsg) {
          urlSyncStatusMsg.style.backgroundColor = 'rgba(239, 68, 68, 0.15)';
          urlSyncStatusMsg.style.color = '#f87171';
          urlSyncStatusMsg.style.border = '1px solid rgba(239, 68, 68, 0.4)';
          urlSyncStatusMsg.textContent = "⚠️ " + err.message;
        }
      } finally {
        btnAutoConfigureUrl.disabled = false;
        btnAutoConfigureUrl.textContent = "⚡ AUTO-CONFIGURE & SYNC FROM URL";
      }
    });
  }

  // Save Configuration Handler
  if (btnSaveConfig) {
    btnSaveConfig.addEventListener('click', async () => {
      const config = {
        matchId: inputMatchId.value,
        matchUrl: inputMatchUrl.value,
        tournament: inputTournament.value,
        pollingInterval: Number(inputPollInterval.value),
        teamA: {
          name: inputTeamAName.value,
          shortName: inputTeamAShort.value
        },
        teamB: {
          name: inputTeamBName.value,
          shortName: inputTeamBShort.value
        }
      };

      btnSaveConfig.disabled = true;
      btnSaveConfig.textContent = "Saving...";
      try {
        await window.dataService.updateConfig(config);
        btnSaveConfig.textContent = "Saved Successfully! ✓";
      } catch (err) {
        alert("Failed to save config: " + err.message);
        btnSaveConfig.textContent = "Save Configuration";
      } finally {
        setTimeout(() => {
          btnSaveConfig.disabled = false;
          btnSaveConfig.textContent = "Save Configuration";
        }, 1500);
      }
    });
  }

  // Reset to Reference Match (ICL 2026 Admin Champions Bangalore vs TSS Cricket)
  if (btnResetReference) {
    btnResetReference.addEventListener('click', async () => {
      if (confirm("Reset match state to Reference Match ID 27644870 (ACB vs TSS)?")) {
        await window.dataService.resetReference();
        if (urlSyncStatusMsg) {
          urlSyncStatusMsg.style.display = 'block';
          urlSyncStatusMsg.style.backgroundColor = 'rgba(16, 185, 129, 0.15)';
          urlSyncStatusMsg.style.color = '#34d399';
          urlSyncStatusMsg.style.border = '1px solid rgba(16, 185, 129, 0.4)';
          urlSyncStatusMsg.textContent = "✓ Reset to Reference Match: ICL 2026 (ACB vs TSS)";
        }
      }
    });
  }

  // Settings Change Handlers
  if (toggleOverlayVisible) {
    toggleOverlayVisible.addEventListener('change', () => {
      window.dataService.updateOverlaySettings({ visible: toggleOverlayVisible.checked });
    });
  }

  if (selectLayout) {
    selectLayout.addEventListener('change', () => {
      window.dataService.updateOverlaySettings({ layout: selectLayout.value });
    });
  }

  if (selectTheme) {
    selectTheme.addEventListener('change', () => {
      window.dataService.updateOverlaySettings({ theme: selectTheme.value });
    });
  }

  if (toggleSponsors) {
    toggleSponsors.addEventListener('change', () => {
      window.dataService.updateOverlaySettings({ showSponsors: toggleSponsors.checked });
    });
  }

  if (inputSponsorText) {
    inputSponsorText.addEventListener('input', () => {
      window.dataService.updateOverlaySettings({ sponsorText: inputSponsorText.value });
    });
  }

  // Simulation Toggle
  if (toggleSimulation) {
    toggleSimulation.addEventListener('change', () => {
      if (toggleSimulation.checked) {
        window.mockMatchEngine.start();
      } else {
        window.mockMatchEngine.stop();
      }
    });
  }

  // Sound Toggle
  if (toggleSound) {
    toggleSound.addEventListener('change', () => {
      if (window.soundService) {
        window.soundService.enabled = toggleSound.checked;
      }
    });
  }

  // Event Triggers
  document.querySelectorAll('[data-trigger-event]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const type = btn.getAttribute('data-trigger-event');
      const message = btn.getAttribute('data-message') || type;
      await window.dataService.triggerEvent(type, message);
    });
  });

  // Scoring Ball Buttons
  document.querySelectorAll('[data-ball]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const ball = btn.getAttribute('data-ball');
      const runs = parseInt(btn.getAttribute('data-runs') || '0', 10);
      await window.dataService.recordBall(ball, runs);
    });
  });

  // Undo Ball
  const btnUndo = document.getElementById('btn-undo-ball');
  if (btnUndo) {
    btnUndo.addEventListener('click', async () => {
      await window.dataService.undo();
    });
  }

  // Preview Background Controls
  document.querySelectorAll('[data-preview-bg]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-preview-bg]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const bg = btn.getAttribute('data-preview-bg');
      if (previewBg) {
        previewBg.className = `preview-background-${bg}`;
      }
    });
  });

  // DataService Listeners
  window.dataService.onUpdate((state) => {
    populateForm(state);
  });

  window.dataService.onError((err) => {
    if (statusBadge && statusText) {
      statusBadge.className = 'status-badge error';
      statusText.textContent = 'CONNECT ERROR';
    }
  });

  window.dataService.startPolling();
});
