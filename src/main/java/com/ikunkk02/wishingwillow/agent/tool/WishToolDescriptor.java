package com.ikunkk02.wishingwillow.agent.tool;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.contract.WishContractType;
import com.ikunkk02.wishingwillow.research.FeatureType;

import java.util.Set;

public record WishToolDescriptor(
        String name,
        String description,
        JsonObject parameters,
        WishToolCategory category,
        boolean alwaysVisible,
        boolean readOnly,
        Set<WishCapability> capabilities,
        Set<WishContractType> contractTypes,
        Set<FeatureType> featureTypes,
        Set<String> supportsSemantics,
        Set<String> unsupportedSemantics
) {
    public WishToolDescriptor {
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        contractTypes = Set.copyOf(contractTypes == null ? Set.of() : contractTypes);
        featureTypes = Set.copyOf(featureTypes == null ? Set.of() : featureTypes);
        supportsSemantics = Set.copyOf(supportsSemantics == null ? Set.of() : supportsSemantics);
        unsupportedSemantics = Set.copyOf(unsupportedSemantics == null ? Set.of() : unsupportedSemantics);
    }

    public WishToolDescriptor(String name, String description, JsonObject parameters,
                              WishToolCategory category, boolean alwaysVisible, boolean readOnly,
                              Set<WishCapability> capabilities, Set<WishContractType> contractTypes,
                              Set<FeatureType> featureTypes) {
        this(name, description, parameters, category, alwaysVisible, readOnly, capabilities,
                contractTypes, featureTypes, Set.of(), Set.of());
    }
}
