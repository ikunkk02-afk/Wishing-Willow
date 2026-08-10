package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class WishPlanPreviewScreen extends Screen {
    @Nullable private final Screen parent; private final String wish;
    @Nullable private final WishInterpretation interpretation; @Nullable private final CapabilityCatalog catalog;
    private final WishPlanResult result; private int scroll;

    public WishPlanPreviewScreen(@Nullable Screen parent,String wish,@Nullable WishInterpretation interpretation,
                                 @Nullable CapabilityCatalog catalog,WishPlanResult result){super(Component.translatable("screen.wishing_willow.plan_preview.title"));this.parent=parent;this.wish=wish;this.interpretation=interpretation;this.catalog=catalog;this.result=result;}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width/2-50,height-28,100,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll=Math.max(0,scroll-(int)Math.signum(delta));return true;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);int max=Math.min(620,width-32);int left=(width-max)/2;List<Component> sections=sections();List<FormattedCharSequence> lines=new ArrayList<>();for(Component section:sections){lines.addAll(font.split(section,max));lines.add(FormattedCharSequence.EMPTY);}int y=28;for(int i=scroll;i<lines.size()&&y<height-38;i++,y+=10)g.drawString(font,lines.get(i),left,y,0xFFD7D3CB);super.render(g,mouseX,mouseY,partial);}
    private List<Component> sections(){List<Component> values=new ArrayList<>();values.add(Component.literal("Original Wish: "+wish));if(interpretation!=null){values.add(Component.literal("Interpretation: "+interpretation.twistedOutcome()));values.add(Component.literal("Required: "+interpretation.requiredCapabilities()));}if(catalog!=null){catalog.matchSets().forEach(set->{values.add(Component.literal("Match "+set.capability()+": "+set.quality()));set.candidates().forEach(c->values.add(Component.literal("  "+c.candidateId()+" "+c.sourceModName()+" / "+c.featureName()+" "+c.matchType()+" "+c.matchScore()+"%"+(c.registryResource()==null?"":" "+c.registryResource().id()))));});}values.add(Component.literal("Plan Result: "+result.state()+" / "+result.error()));if(result.draft()!=null){values.add(Component.literal("Summary: "+result.draft().summary()));result.draft().steps().forEach(s->values.add(Component.literal(s.stepIndex()+". "+s.timing()+" "+s.action()+" "+s.candidateId()+" delay="+s.delaySeconds()+"s")));}return values;}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);} @Override public boolean isPauseScreen(){return false;}
}
