package com.ikunkk02.wishingwillow.agent.skill;

import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import dev.langchain4j.skills.Skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WishAgentSkillManager {
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public WishAgentSkillManager(WishAgentSkillLoader loader) {
        for (Skill skill : loader.load()) skills.put(skill.name(), skill);
    }

    public List<Skill> skills() { return List.copyOf(skills.values()); }

    public String availableSkills() {
        return skills.values().stream().map(skill -> skill.name() + ": " + skill.description())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public String activate(WishAgentSession session, String name) {
        Skill skill = skills.get(name);
        if (skill == null) throw new IllegalArgumentException("UNKNOWN_SKILL");
        if (!WishAgentSkillLoader.WISH_SKILL.equals(name)) throw new IllegalArgumentException("SKILL_NOT_ALLOWED");
        session.activateSkill();
        return skill.content();
    }
}
