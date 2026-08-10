package com.ikunkk02.wishingwillow.research.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebResearchBudgetTest {
    @Test
    void enforcesEveryPerModLimit() {
        WebResearchBudget budget = new WebResearchBudget();
        for (int i = 0; i < WebResearchBudget.MAX_SEARCH_QUERIES; i++) budget.claimSearch();
        for (int i = 0; i < WebResearchBudget.MAX_CANDIDATE_PAGES; i++) budget.claimCandidate();
        for (int i = 0; i < WebResearchBudget.MAX_FETCHED_PAGES; i++) budget.claimFetch();

        assertThrows(WebResearchBudget.BudgetException.class, budget::claimSearch);
        assertThrows(WebResearchBudget.BudgetException.class, budget::claimCandidate);
        assertThrows(WebResearchBudget.BudgetException.class, budget::claimFetch);
        assertEquals(0, budget.searchesRemaining());
    }
}
