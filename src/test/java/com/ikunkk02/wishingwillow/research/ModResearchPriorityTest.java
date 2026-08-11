package com.ikunkk02.wishingwillow.research;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModResearchPriorityTest {
    @Test void contentNeededForWishesIsScheduledBeforeInfrastructure(){
        List<ModCategory> ordered=List.of(ModCategory.values()).stream()
                .sorted(java.util.Comparator.comparingInt(ModResearchPriority::value)).toList();
        assertEquals(List.of(ModCategory.HORROR,ModCategory.MOBS,ModCategory.DIMENSION,
                ModCategory.CONTENT,ModCategory.MAGIC,ModCategory.COMBAT,ModCategory.WORLDGEN,
                ModCategory.TECHNOLOGY,ModCategory.UTILITY,ModCategory.UNKNOWN,ModCategory.COSMETIC,
                ModCategory.LIBRARY,ModCategory.API,ModCategory.PERFORMANCE),ordered);
        assertTrue(ModResearchPriority.value(ModCategory.LIBRARY)>=100);
        assertTrue(ModResearchPriority.value(ModCategory.API)>=100);
        assertTrue(ModResearchPriority.value(ModCategory.PERFORMANCE)>=100);
    }
}
