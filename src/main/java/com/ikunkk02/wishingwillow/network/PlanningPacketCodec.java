package com.ikunkk02.wishingwillow.network;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.CandidateSourceKind;
import com.ikunkk02.wishingwillow.planning.CapabilityCandidate;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.CapabilityMatchSet;
import com.ikunkk02.wishingwillow.planning.MatchType;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PlanningPacketCodec {
    private PlanningPacketCodec() { }

    public static void writeInterpretation(FriendlyByteBuf b, WishInterpretation i) {
        b.writeUtf(WishInterpretationValidator.toJson(i), 32 * 1024);
    }

    public static WishInterpretation readInterpretation(FriendlyByteBuf b) {
        return WishInterpretationValidator.parseAndValidate(b.readUtf(32 * 1024));
    }

    public static void writeContext(FriendlyByteBuf b, WishContextSnapshot c) {
        b.writeUtf(c.dimension(),256); b.writeLong(c.gameTime()); b.writeUtf(c.dayPhase(),16); b.writeUtf(c.weather(),16);
        b.writeFloat(c.health()); b.writeFloat(c.maxHealth()); b.writeVarInt(c.hunger()); b.writeVarInt(c.experienceLevel());
        b.writeUtf(c.gameMode(),32); b.writeUtf(c.biome(),256); b.writeInt(c.approximateY()); b.writeUtf(c.environmentType(),32);
        b.writeUtf(c.heldItem(),256); b.writeVarInt(c.armorSummary().size()); c.armorSummary().forEach(v->b.writeUtf(v,256));
        b.writeVarInt(c.nearbyEntities().size()); c.nearbyEntities().forEach(v->{b.writeUtf(v.entityType(),256);b.writeVarInt(v.count());});
        b.writeVarInt(c.nearbyHostileCount()); b.writeVarInt(c.nearbyPassiveCount());
    }

    public static WishContextSnapshot readContext(FriendlyByteBuf b) {
        String dimension=b.readUtf(256); long gameTime=b.readLong(); String phase=b.readUtf(16); String weather=b.readUtf(16);
        float health=b.readFloat(), maxHealth=b.readFloat(); int hunger=b.readVarInt(), xp=b.readVarInt();
        String gameMode=b.readUtf(32), biome=b.readUtf(256); int y=b.readInt(); String environment=b.readUtf(32), held=b.readUtf(256);
        int armorCount=b.readVarInt(); if(armorCount<0||armorCount>4) throw new IllegalArgumentException("INVALID_CONTEXT");
        List<String> armor=new ArrayList<>(); for(int x=0;x<armorCount;x++) armor.add(b.readUtf(256));
        int nearbyCount=b.readVarInt(); if(nearbyCount<0||nearbyCount>20) throw new IllegalArgumentException("INVALID_CONTEXT");
        List<WishContextSnapshot.NearbyEntitySummary> nearby=new ArrayList<>();
        for(int x=0;x<nearbyCount;x++) nearby.add(new WishContextSnapshot.NearbyEntitySummary(b.readUtf(256),b.readVarInt()));
        return new WishContextSnapshot(dimension,gameTime,phase,weather,health,maxHealth,hunger,xp,gameMode,biome,y,
                environment,held,armor,nearby,b.readVarInt(),b.readVarInt());
    }

    public static void writeExecutionSettings(FriendlyByteBuf b, ExecutionSettingsSnapshot s) {
        b.writeBoolean(s.enabled()); b.writeBoolean(s.thirdPartyEntities());
        b.writeBoolean(s.blockModification()); b.writeBoolean(s.explosions());
        b.writeBoolean(s.destructiveExplosions()); b.writeBoolean(s.crossDimensionTeleport());
        b.writeBoolean(s.debugSafeMode()); b.writeVarInt(s.maximumDestructiveSeverity());
    }

    public static ExecutionSettingsSnapshot readExecutionSettings(FriendlyByteBuf b) {
        return new ExecutionSettingsSnapshot(b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readVarInt(), false);
    }

    public static void writeCatalog(FriendlyByteBuf b, CapabilityCatalog catalog) {
        b.writeUtf(catalog.knowledgeState(),32); b.writeUtf(catalog.knowledgeDigest(),128); b.writeUtf(catalog.registryDigest(),128);
        b.writeVarInt(catalog.candidates().size());
        for(CapabilityCandidate c:catalog.candidates()) {
            b.writeUtf(c.candidateId(),32); b.writeEnum(c.requestedCapability()); b.writeEnum(c.providedCapability()); b.writeEnum(c.matchType());
            b.writeEnum(c.sourceKind()); b.writeUtf(c.sourceModId(),128); b.writeUtf(c.sourceModName(),256); b.writeUtf(c.sourceModVersion(),128);
            b.writeUtf(c.featureName(),128); b.writeEnum(c.featureType()); b.writeBoolean(c.registryResource()!=null);
            if(c.registryResource()!=null){b.writeEnum(c.registryResource().type());b.writeUtf(c.registryResource().id(),256);}
            b.writeEnum(c.knowledgeLevel()); b.writeDouble(c.researchConfidence()); b.writeDouble(c.featureConfidence());
            b.writeVarInt(c.horrorScore()); b.writeVarInt(c.wishRelevance()); b.writeVarInt(c.riskScore()); b.writeVarInt(c.matchScore());
        }
    }

    public static CapabilityCatalog readCatalog(FriendlyByteBuf b) {
        String state=b.readUtf(32), knowledge=b.readUtf(128), registry=b.readUtf(128); int count=b.readVarInt();
        if(count<0||count>CapabilityCatalog.MAX_CANDIDATES) throw new IllegalArgumentException("INVALID_CATALOG");
        List<CapabilityCandidate> candidates=new ArrayList<>();
        for(int x=0;x<count;x++) {
            String id=b.readUtf(32); WishCapability requested=b.readEnum(WishCapability.class), provided=b.readEnum(WishCapability.class);
            MatchType match=b.readEnum(MatchType.class); CandidateSourceKind kind=b.readEnum(CandidateSourceKind.class);
            String modId=b.readUtf(128), modName=b.readUtf(256), version=b.readUtf(128), feature=b.readUtf(128); FeatureType featureType=b.readEnum(FeatureType.class);
            VerifiedRegistryResource resource=b.readBoolean()?new VerifiedRegistryResource(b.readEnum(RegistryEntryType.class),b.readUtf(256)):null;
            candidates.add(new CapabilityCandidate(id,requested,provided,match,kind,modId,modName,version,feature,featureType,resource,"",
                    b.readEnum(KnowledgeLevel.class),b.readDouble(),b.readDouble(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        }
        Map<WishCapability,List<CapabilityCandidate>> grouped=new EnumMap<>(WishCapability.class);
        candidates.forEach(c->grouped.computeIfAbsent(c.requestedCapability(),ignored->new ArrayList<>()).add(c));
        List<CapabilityMatchSet> sets=new ArrayList<>(); grouped.forEach((cap,values)->sets.add(new CapabilityMatchSet(cap,values.isEmpty()?MatchType.UNSATISFIED:values.get(0).matchType(),values)));
        return CapabilityCatalog.create(sets,candidates,state,knowledge,registry);
    }
}
