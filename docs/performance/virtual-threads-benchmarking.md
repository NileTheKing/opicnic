# 🚀 Virtual Threads & I/O Pipeline Optimization Report

## 📅 Date: 2026-04-19
## 🎯 Goal: Reduce Latency from 1.13s to sub-200ms and eliminate system bottlenecks.

---

### 🕵️‍♂️ Phase 1: Breaking the Thread Pool Barrier
- **Status:** Initial test showed 1.1s latency and 96 RPS with 500 VUs.
- **Problem:** Internal business logic used a `FixedThreadPool(10)`, causing **Thread Starvation**.
- **Action:** Switched to Java 21 `VirtualThreadPerTaskExecutor`.
- **Result:** RPS jumped from **96 to 242 (+250%)**.

### 🕵️‍♂️ Phase 2: The DB Connection Pool "Phantom"
- **Hypothesis:** DB Pool size (10) is causing wait times.
- **Bug Discovery:** Full-cycle tests failed (0% success). Log analysis revealed an `IllegalArgumentException` due to an **Enum mismatch** (`topic=Introduction` was not in DB).
- **Correction:** Fixed data topic to `MOVIE_WATCHING`.
- **Validation:** Tested Pool 10 vs. Pool 50. Surprising result: **No significant change at 150 VUs.**
- **Insight:** Virtual Threads are so efficient at returning connections that 10 slots could handle 150 concurrent users. The *real* bottleneck was elsewhere.

### 🕵️‍♂️ Phase 3: Pinning Trace & Payload Isolation
- **Pinning Check:** Ran with `-Djdk.tracePinnedThreads=short`. **Result: No Pinning detected.** Virtual threads are scaling freely.
- **Tiny File Experiment:** Switched 1MB audio to 1KB.
- **Result:** Latency dropped from **845ms to 44ms**.
- **Conclusion:** The primary bottleneck is **Multipart Disk I/O & Network Payload Processing**.

### 🕵️‍♂️ Phase 4: Logging Strategy Optimization
- **Problem:** `DEBUG` logging for SQL and Hikari was generating massive I/O.
- **Action:** Switched to `INFO` level.
- **Result:** p95 latency improved from **1.13s to 879ms (22% improvement)**.

### 🕵️‍♂️ Phase 5: In-Memory Multipart Relay (The Final Breakthrough)
- **Problem:** Tomcat/Spring default behavior writes files >10KB to disk. 500 concurrent writes = Disk I/O death.
- **Action:**
  1. Set `file-size-threshold: 2MB` to keep 1MB audio files in RAM.
  2. Implemented `InputStream` relay in `STTService` and `FeedbackServiceV2` to bypass buffering.
- **Final Result (Mock STT):**
  - **Avg Latency:** 249ms (from 404ms)
  - **p95 Latency:** **659ms (from 1.13s)**
  - **Peak Throughput:** **652 req/s**
  - **Stability:** 99.9% Success rate (limited only by OS Network Buffer).

---

### 📊 Final Performance Metrics (500 VUs, 1MB Payload)

| Metric | Baseline (Fixed Pool) | Intermediate (VT + Debug) | **Optimized (VT + In-Memory)** |
| :--- | :--- | :--- | :--- |
| **RPS** | 96 req/s | 537 req/s | **652 req/s** |
| **Avg Latency** | 1.1s | 404ms | **249ms** |
| **p95 Latency** | >2.0s | 1.13s | **659ms** |
| **Success Rate** | 98% | 99% | **99.9%** |

---

### 🔍 Engineering Insights & "Lessons Learned"
1. **Measurement over Guesswork:** We initially blamed the DB Pool, but data proved it was Disk I/O.
2. **Virtual Threads are Lean:** They reduced context-switching costs, allowing us to hit 600+ RPS on a single machine.
3. **Physical Limits:** `no buffer space available` warnings indicate we have reached the OS-level TCP buffer limits. Further gains require OS tuning (`sysctl`).
4. **The Cost of Logging:** Synchronous `DEBUG` logs can be as heavy as the actual business logic under high load.

---

### 🚀 Future Roadmap: "Real-World STT Integration"
- [ ] **Disable Mock STT:** Test the relay pipeline with the actual Python FastAPI server.
- [ ] **Zero-Copy Streaming:** Move from `getParts()` to direct `HttpServletRequest.getInputStream()` parsing.
- [ ] **Structured Concurrency:** Refactor `FeedbackServiceV2` to use `StructuredTaskScope` for better resilience.
