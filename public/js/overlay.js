/**
 * Broadcast Overlay Controller for OBS Studio
 * Renders television broadcast graphics, updates score in real-time,
 * and triggers event animations (4, 6, Wicket, Milestones).
 */
document.addEventListener('DOMContentLoaded', () => {
  const container = document.getElementById('overlay-container');
  const bottomBar = document.getElementById('bottom-bar');

  // DOM Elements - Header
  const tournamentText = document.getElementById('tournament-text');
  const matchStatusText = document.getElementById('match-status-text');
  const staleWarningBadge = document.getElementById('stale-warning-badge');
  const sponsorTag = document.getElementById('sponsor-tag');

  // DOM Elements - Current Batting Team Scorecard
  const battingTeamCard = document.getElementById('batting-team-card');
  const battingTeamFlag = document.getElementById('batting-team-flag');
  const battingTeamShort = document.getElementById('batting-team-short');
  const battingTeamFull = document.getElementById('batting-team-full');
  const battingTeamScore = document.getElementById('batting-team-score');
  const battingTeamOvers = document.getElementById('batting-team-overs');
  const battingTargetPill = document.getElementById('batting-target-pill');

  // Batters
  const strikerName = document.getElementById('striker-name');
  const strikerRuns = document.getElementById('striker-runs');
  const strikerBalls = document.getElementById('striker-balls');
  const strikerBoundaries = document.getElementById('striker-boundaries');
  const nonStrikerName = document.getElementById('nonstriker-name');
  const nonStrikerRuns = document.getElementById('nonstriker-runs');
  const nonStrikerBalls = document.getElementById('nonstriker-balls');
  const nonStrikerBoundaries = document.getElementById('nonstriker-boundaries');

  // Bowler
  const bowlerName = document.getElementById('bowler-name');
  const bowlerFigures = document.getElementById('bowler-figures');
  const bowlerEcon = document.getElementById('bowler-econ');

  // Equations & Over
  const crrValue = document.getElementById('crr-value');
  const targetEquation = document.getElementById('target-equation');
  const thisOverBallsContainer = document.getElementById('this-over-balls');

  // Event Banner elements
  const eventBannerWrapper = document.getElementById('event-banner-wrapper');
  const eventCardFour = document.getElementById('event-card-four');
  const eventCardSix = document.getElementById('event-card-six');
  const eventCardWicket = document.getElementById('event-card-wicket');
  const eventCardMilestone = document.getElementById('event-card-milestone');
  const milestoneTitle = document.getElementById('milestone-title');
  const milestoneSubtitle = document.getElementById('milestone-subtitle');

  // Innings break / result announcement
  const announcementCard = document.getElementById('full-screen-announcement');
  const announcementTitle = document.getElementById('announcement-title');
  const announcementDetail = document.getElementById('announcement-detail');
  const announcementSubdetail = document.getElementById('announcement-subdetail');

  let bannerTimeout = null;
  let lastProcessedAnimationTimestamp = 0;

  // Render State Update
  function render(state) {
    if (!state) return;

    // Apply Theme and Layout
    const settings = state.overlaySettings || {};
    if (settings.theme) {
      document.body.setAttribute('data-theme', settings.theme);
    }
    
    // Visibility
    if (settings.visible === false) {
      container.classList.add('hidden');
    } else {
      container.classList.remove('hidden');
    }

    // Layout
    container.className = settings.visible === false ? 'hidden' : '';
    if (settings.layout) {
      container.classList.add(`layout-${settings.layout.replace('_', '-')}`);
    }

    // Tournament & Header
    if (tournamentText) tournamentText.textContent = state.tournament || 'CRICKET TOURNAMENT';
    if (matchStatusText) matchStatusText.textContent = state.statusText || 'LIVE';
    if (staleWarningBadge) {
      if (state.isStale) {
        staleWarningBadge.classList.add('active');
      } else {
        staleWarningBadge.classList.remove('active');
      }
    }

    if (sponsorTag) {
      if (settings.showSponsors && settings.sponsorText) {
        sponsorTag.textContent = settings.sponsorText;
        sponsorTag.style.display = 'block';
      } else {
        sponsorTag.style.display = 'none';
      }
    }

    // CURRENT BATTING TEAM RENDERING (Only display current batting team)
    let batting = null;
    if (state.teamA && state.teamA.isBatting) batting = state.teamA;
    else if (state.teamB && state.teamB.isBatting) batting = state.teamB;
    else if (state.battingTeam && state.teamA && state.battingTeam === state.teamA.shortName) batting = state.teamA;
    else if (state.battingTeam && state.teamB && state.battingTeam === state.teamB.shortName) batting = state.teamB;
    else batting = state.teamB || state.teamA;

    if (batting) {
      if (battingTeamShort) battingTeamShort.textContent = batting.shortName || state.battingTeam || 'BAT';
      if (battingTeamFull) battingTeamFull.textContent = batting.name || '';
      if (battingTeamScore) battingTeamScore.textContent = `${batting.score}/${batting.wickets}`;
      if (battingTeamOvers) battingTeamOvers.textContent = `(${batting.overs} ov)`;
      if (battingTeamFlag && batting.color) battingTeamFlag.style.backgroundColor = batting.color;

      if (battingTargetPill) {
        if (state.currentInnings === 2 && state.target) {
          battingTargetPill.textContent = `TGT: ${state.target}`;
          battingTargetPill.style.display = 'inline-block';
        } else {
          battingTargetPill.style.display = 'none';
        }
      }
    }

    // Batters
    if (state.striker) {
      if (strikerName) strikerName.textContent = state.striker.name;
      if (strikerRuns) strikerRuns.textContent = state.striker.runs;
      if (strikerBalls) strikerBalls.textContent = `(${state.striker.balls})`;
      if (strikerBoundaries) strikerBoundaries.textContent = `${state.striker.fours}x4, ${state.striker.sixes}x6`;
    }

    if (state.nonStriker) {
      if (nonStrikerName) nonStrikerName.textContent = state.nonStriker.name;
      if (nonStrikerRuns) nonStrikerRuns.textContent = state.nonStriker.runs;
      if (nonStrikerBalls) nonStrikerBalls.textContent = `(${state.nonStriker.balls})`;
      if (nonStrikerBoundaries) nonStrikerBoundaries.textContent = `${state.nonStriker.fours}x4, ${state.nonStriker.sixes}x6`;
    }

    // Bowler
    if (state.bowler) {
      if (bowlerName) bowlerName.textContent = state.bowler.name;
      if (bowlerFigures) {
        bowlerFigures.textContent = `${state.bowler.wickets}/${state.bowler.runs} (${state.bowler.overs} ov)`;
      }
      if (bowlerEcon) bowlerEcon.textContent = `Econ: ${state.bowler.economy}`;
    }

    // Equations
    if (crrValue) crrValue.textContent = state.crr;
    if (targetEquation) {
      if (state.currentInnings === 2 && state.target) {
        if (state.requiredRuns <= 0) {
          targetEquation.textContent = "TARGET REACHED";
        } else {
          targetEquation.textContent = `Need ${state.requiredRuns} off ${state.ballsRemaining}b (RRR ${state.rrr})`;
        }
      } else {
        targetEquation.textContent = `1st Innings · CRR ${state.crr}`;
      }
    }

    // This Over Balls
    if (thisOverBallsContainer && state.thisOver) {
      thisOverBallsContainer.innerHTML = '';
      state.thisOver.forEach(ball => {
        const bubble = document.createElement('div');
        bubble.className = 'ball-bubble';
        bubble.textContent = ball;

        if (ball === '0') bubble.classList.add('dot');
        else if (ball === '4') bubble.classList.add('four');
        else if (ball === '6') bubble.classList.add('six');
        else if (ball === 'W') bubble.classList.add('wicket');
        else if (ball === 'Wd' || ball === 'Nb') bubble.classList.add('extra');
        else bubble.classList.add('run');

        thisOverBallsContainer.appendChild(bubble);
      });
    }

    // Check Active Match Announcement
    if (state.status === 'completed' && state.matchResult) {
      const bTeam = state.battingTeam === state.teamA.shortName ? state.teamA : state.teamB;
      const oTeam = state.battingTeam === state.teamA.shortName ? state.teamB : state.teamA;
      showAnnouncement("MATCH CONCLUDED", state.matchResult, `${bTeam.shortName} ${bTeam.score}/${bTeam.wickets} vs ${oTeam.shortName} ${oTeam.score}/${oTeam.wickets}`);
    } else if (state.status === 'innings_break') {
      showAnnouncement("INNINGS BREAK", `TARGET: ${state.target} RUNS`, `${state.bowlingTeam} to chase ${state.target} in ${state.totalOvers} overs`);
    } else {
      hideAnnouncement();
    }

    // Check Trigger Animation from state
    if (state.activeAnimation && state.activeAnimation.timestamp > lastProcessedAnimationTimestamp) {
      lastProcessedAnimationTimestamp = state.activeAnimation.timestamp;
      triggerBannerAnimation(state.activeAnimation.type, state.activeAnimation.message);
    }
  }

  // Trigger Match Event Animation
  function triggerBannerAnimation(type, message) {
    if (!eventBannerWrapper) return;
    
    eventCardFour.style.display = 'none';
    eventCardSix.style.display = 'none';
    eventCardWicket.style.display = 'none';
    eventCardMilestone.style.display = 'none';

    if (bannerTimeout) clearTimeout(bannerTimeout);

    if (type === 'FOUR') {
      eventCardFour.style.display = 'flex';
      const sub = document.getElementById('event-subtitle-four');
      if (sub && message) sub.textContent = message;
      if (window.soundService) window.soundService.playFour();
    } else if (type === 'SIX') {
      eventCardSix.style.display = 'flex';
      const sub = document.getElementById('event-subtitle-six');
      if (sub && message) sub.textContent = message;
      if (window.soundService) window.soundService.playSix();
    } else if (type === 'WICKET') {
      eventCardWicket.style.display = 'flex';
      const sub = document.getElementById('event-subtitle-wicket');
      if (sub && message) sub.textContent = message;
      if (window.soundService) window.soundService.playWicket();
    } else if (type === 'MILESTONE') {
      eventCardMilestone.style.display = 'flex';
      if (milestoneTitle) milestoneTitle.textContent = "MILESTONE!";
      if (milestoneSubtitle && message) milestoneSubtitle.textContent = message;
      if (window.soundService) window.soundService.playMilestone();
    }

    eventBannerWrapper.classList.add('active');

    bannerTimeout = setTimeout(() => {
      eventBannerWrapper.classList.remove('active');
    }, 4500);
  }

  function showAnnouncement(title, detail, sub) {
    if (!announcementCard) return;
    announcementTitle.textContent = title;
    announcementDetail.textContent = detail;
    announcementSubdetail.textContent = sub;
    announcementCard.classList.add('active');
  }

  function hideAnnouncement() {
    if (announcementCard) announcementCard.classList.remove('active');
  }

  // Hook Data Service
  window.dataService.onUpdate((state) => {
    render(state);
  });

  window.dataService.onEvent((eventData) => {
    if (eventData && eventData.type) {
      triggerBannerAnimation(eventData.type, eventData.message);
    }
  });

  window.dataService.startPolling();
});
