package com.ikunkk02.wishingwillow.agent.skill;

import dev.langchain4j.skills.Skills;

/** Keeps the experimental Skills API behind a single project-owned type. */
public final class LangChain4jSkillAdapter {
    private final WishAgentSkillManager manager;
    private final Skills skills;

    public LangChain4jSkillAdapter(WishAgentSkillManager manager) {
        this.manager = java.util.Objects.requireNonNull(manager);
        this.skills = Skills.from(manager.skills());
    }

    public String formatAvailableSkills() { return skills.formatAvailableSkills(); }
    public WishAgentSkillManager manager() { return manager; }
}
