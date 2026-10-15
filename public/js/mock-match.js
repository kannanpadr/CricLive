/**
 * Realistic Match Simulation Engine for Match 27644870
 * (IISC Champions League 2026: Admin Champions Bangalore vs TSS Cricket)
 * Generates live balls, strike rotation, bowler changes, and celebrations.
 */
class MockMatchEngine {
  constructor(dataService) {
    this.dataService = dataService;
    this.timer = null;
    this.isRunning = false;
    this.intervalMs = 9000; // Realistic 9s interval between deliveries

    // Weighted outcomes for realistic T20 cricket chase
    this.ballPool = [
      { outcome: '0', runs: 0, weight: 35 },
      { outcome: '1', runs: 1, weight: 30 },
      { outcome: '2', runs: 2, weight: 12 },
      { outcome: '4', runs: 4, weight: 11 },
      { outcome: '6', runs: 6, weight: 6 },
      { outcome: 'W', runs: 0, weight: 4 },
      { outcome: 'Wd', runs: 1, weight: 2 }
    ];
  }

  start() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.scheduleNextBall();
  }

  stop() {
    this.isRunning = false;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  scheduleNextBall() {
    if (!this.isRunning) return;
    const delay = Math.floor(Math.random() * 4000) + 7000; // 7s to 11s jitter
    this.timer = setTimeout(async () => {
      if (!this.isRunning) return;
      await this.deliverBall();
      this.scheduleNextBall();
    }, delay);
  }

  async deliverBall() {
    const selected = this.pickWeightedBall();
    try {
      await this.dataService.recordBall(selected.outcome, selected.runs);
    } catch (err) {
      console.warn("Failed to deliver mock ball:", err);
    }
  }

  pickWeightedBall() {
    const totalWeight = this.ballPool.reduce((sum, item) => sum + item.weight, 0);
    let rand = Math.random() * totalWeight;
    for (const item of this.ballPool) {
      if (rand < item.weight) {
        return item;
      }
      rand -= item.weight;
    }
    return this.ballPool[0];
  }
}

window.mockMatchEngine = new MockMatchEngine(window.dataService);
