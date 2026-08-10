package com.ikunkk02.wishingwillow.research.web;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class WebResearchBudget {
    public static final int MAX_SEARCH_QUERIES = 5;
    public static final int MAX_CANDIDATE_PAGES = 3;
    public static final int MAX_FETCHED_PAGES = 5;
    public static final int MAX_REDIRECTS = 4;
    public static final int MAX_CONTENT_BYTES = 512 * 1024;
    public static final int MAX_EXTRACTED_CHARS = 40 * 1024;
    public static final Duration MAX_RESEARCH_TIME = Duration.ofSeconds(60);

    private final long deadlineNanos;
    private final AtomicInteger searches = new AtomicInteger();
    private final AtomicInteger candidates = new AtomicInteger();
    private final AtomicInteger fetches = new AtomicInteger();

    public WebResearchBudget() {
        this(System.nanoTime() + MAX_RESEARCH_TIME.toNanos());
    }

    public WebResearchBudget(int searchesUsed, int candidatesUsed, int fetchesUsed) {
        this();
        searches.set(Math.max(0, searchesUsed));
        candidates.set(Math.max(0, candidatesUsed));
        fetches.set(Math.max(0, fetchesUsed));
    }

    WebResearchBudget(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public void claimSearch() { claim(searches, MAX_SEARCH_QUERIES, "SEARCH_BUDGET_EXHAUSTED"); }
    public void claimCandidate() { claim(candidates, MAX_CANDIDATE_PAGES, "CANDIDATE_BUDGET_EXHAUSTED"); }
    public void claimFetch() { claim(fetches, MAX_FETCHED_PAGES, "FETCH_BUDGET_EXHAUSTED"); }

    public int searchesUsed() { return searches.get(); }
    public int candidatesUsed() { return candidates.get(); }
    public int fetchesUsed() { return fetches.get(); }
    public int searchesRemaining() { return Math.max(0, MAX_SEARCH_QUERIES - searches.get()); }

    public void checkTime() {
        if (System.nanoTime() > deadlineNanos) throw new BudgetException("RESEARCH_TIME_EXHAUSTED");
    }

    private void claim(AtomicInteger counter, int max, String code) {
        checkTime();
        if (counter.incrementAndGet() > max) {
            counter.decrementAndGet();
            throw new BudgetException(code);
        }
    }

    public static final class BudgetException extends RuntimeException {
        public BudgetException(String code) { super(code); }
    }
}
