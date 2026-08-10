package com.ikunkk02.wishingwillow.execution;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WishExecutionIdempotencyTest {
    @Test void samePlanAndSessionCanOnlyBeAcceptedOnce(){UUID plan=UUID.randomUUID(),session=UUID.randomUUID(),owner=UUID.randomUUID();WishExecutionSavedData data=new WishExecutionSavedData();int accepted=0;for(int i=0;i<10;i++)if(data.add(new WishExecutionRecord(UUID.randomUUID(),plan,session,owner,1,0)))accepted++;assertEquals(1,accepted);assertNotNull(data.byPlan(plan));assertNotNull(data.bySession(session));}
    @Test void structuredResultCarriesStableIdAndDetailedError(){UUID id=UUID.randomUUID();WishExecutionAcceptResult existing=WishExecutionAcceptResult.alreadyAccepted(id);assertTrue(existing.accepted());assertEquals(WishExecutionAcceptError.ALREADY_ACCEPTED,existing.error());assertEquals(id,existing.executionId());WishExecutionAcceptResult rejected=WishExecutionAcceptResult.rejected(WishExecutionAcceptError.DEBUG_SAFE_MODE,"block modification disabled");assertFalse(rejected.accepted());assertEquals(WishExecutionAcceptError.DEBUG_SAFE_MODE,rejected.error());assertEquals("block modification disabled",rejected.detail());}
}
