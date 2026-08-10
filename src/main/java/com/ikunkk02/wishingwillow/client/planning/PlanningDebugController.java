package com.ikunkk02.wishingwillow.client.planning;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpreter;
import com.ikunkk02.wishingwillow.client.gui.WishPlanPreviewScreen;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.CapabilityMatcher;
import com.ikunkk02.wishingwillow.planning.RegistrySnapshotEnvironment;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import com.ikunkk02.wishingwillow.planning.WishPlanner;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public final class PlanningDebugController {
    private PlanningDebugController() { }

    public static void run(@Nullable Screen parent, String wish, @Nullable WishInterpretation supplied) {
        Minecraft minecraft=Minecraft.getInstance(); AiConfig config=AiConfigManager.getInstance().get();
        if(!config.isConfigured()||minecraft.player==null)return;
        WishContextSnapshot context=ClientWishContextCollector.collect();
        CompletableFuture<WishInterpretation> interpretation=supplied==null
                ?new WishInterpreter(AiService.getInstance()).interpret(config,wish).thenApply(result->result.interpretation())
                :CompletableFuture.completedFuture(supplied);
        interpretation.thenCompose(value->{
            if(value==null)return CompletableFuture.completedFuture(new Preview(null,null,WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED)));
            var manager=ModResearchManager.getInstance(); var registry=manager.registrySnapshot();
            CapabilityCatalog catalog=new CapabilityMatcher().match(wish,value,manager.knowledgeBase().snapshot(),registry);
            return new WishPlanner().plan(config,wish,value,context,catalog,new RegistrySnapshotEnvironment(registry))
                    .thenApply(result->new Preview(value,catalog,result));
        }).exceptionally(error->new Preview(supplied,null,WishPlanResult.failed(WishPlanError.UNKNOWN)))
                .thenAccept(preview->minecraft.execute(()->minecraft.setScreen(new WishPlanPreviewScreen(parent,wish,preview.interpretation,preview.catalog,preview.result))));
    }

    private record Preview(WishInterpretation interpretation,CapabilityCatalog catalog,WishPlanResult result){}
}
