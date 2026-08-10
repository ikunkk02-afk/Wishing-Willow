package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.RequestExecutionSettingsPacket;
import com.ikunkk02.wishingwillow.network.packet.UpdateExecutionSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExecutionSettingsScreen extends Screen {
    private final Screen parent;private ExecutionSettingsSnapshot value=new ExecutionSettingsSnapshot(true,true,true,true,false,false,true,80,false);
    public ExecutionSettingsScreen(Screen parent){super(Component.translatable("screen.wishing_willow.execution.title"));this.parent=parent;}
    @Override protected void init(){rebuild();ModNetworking.sendToServer(new RequestExecutionSettingsPacket());}
    public void apply(ExecutionSettingsSnapshot settings){value=settings;if(minecraft!=null)minecraft.execute(this::rebuild);}
    private void rebuild(){clearWidgets();int x=width/2-150,y=38;addToggle(x,y,0);addToggle(x+155,y,1);addToggle(x,y+24,2);addToggle(x+155,y+24,3);addToggle(x,y+48,4);addToggle(x+155,y+48,5);addToggle(x,y+72,6);addRenderableWidget(Button.builder(label("screen.wishing_willow.execution.max_severity",Integer.toString(value.maximumDestructiveSeverity())),b->{value=new ExecutionSettingsSnapshot(value.enabled(),value.thirdPartyEntities(),value.blockModification(),value.explosions(),value.destructiveExplosions(),value.crossDimensionTeleport(),value.debugSafeMode(),(value.maximumDestructiveSeverity()+20)%120,value.canEdit());rebuild();}).bounds(x+155,y+72,145,20).build()).active=value.canEdit();Button save=addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.execution.save"),b->ModNetworking.sendToServer(new UpdateExecutionSettingsPacket(value))).bounds(x,y+108,145,20).build());save.active=value.canEdit();addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+155,y+108,145,20).build());}
    private void addToggle(int x,int y,int index){String key=switch(index){case 0->"enabled";case 1->"third_party";case 2->"blocks";case 3->"explosions";case 4->"destructive";case 5->"cross_dimension";default->"safe_mode";};boolean current=get(index);Button button=addRenderableWidget(Button.builder(label("screen.wishing_willow.execution."+key,Component.translatable(current?"options.on":"options.off").getString()),b->{set(index,!get(index));rebuild();}).bounds(x,y,145,20).build());button.active=value.canEdit();}
    private Component label(String key,String status){return Component.translatable(key).append(": ").append(status);}
    private boolean get(int i){return switch(i){case 0->value.enabled();case 1->value.thirdPartyEntities();case 2->value.blockModification();case 3->value.explosions();case 4->value.destructiveExplosions();case 5->value.crossDimensionTeleport();default->value.debugSafeMode();};}
    private void set(int i,boolean v){value=new ExecutionSettingsSnapshot(i==0?v:value.enabled(),i==1?v:value.thirdPartyEntities(),i==2?v:value.blockModification(),i==3?v:value.explosions(),i==4?v:value.destructiveExplosions(),i==5?v:value.crossDimensionTeleport(),i==6?v:value.debugSafeMode(),value.maximumDestructiveSeverity(),value.canEdit());}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,18,0xffffffff);if(!value.canEdit())g.drawCenteredString(font,Component.translatable("screen.wishing_willow.execution.read_only"),width/2,height-40,0xffffaa55);super.render(g,mx,my,partial);}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
