package com.ikunkk02.wishingwillow.agent.skill;

import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;

import java.util.List;

public final class WishAgentSkillLoader {
    public static final String ROOT = "skills";
    public static final String WISH_SKILL = "fulfill-minecraft-wish-with-tools";

    public List<Skill> load() {
        return List.copyOf(ClassPathSkillLoader.loadSkills(ROOT));
    }
}
