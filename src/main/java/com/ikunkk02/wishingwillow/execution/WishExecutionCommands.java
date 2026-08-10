package com.ikunkk02.wishingwillow.execution;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.UUID;

public final class WishExecutionCommands {
    private WishExecutionCommands(){}
    public static void register(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("wishingwillow").requires(source->source.hasPermission(2)).then(Commands.literal("execution")
            .then(Commands.literal("list").executes(context->{var all=WishExecutionSavedData.get(context.getSource().getServer()).all();context.getSource().sendSuccess(()->Component.literal("Wish executions: "+all.size()),false);all.stream().filter(r->!r.state().terminal()).limit(20).forEach(r->context.getSource().sendSuccess(()->Component.literal(r.executionId()+" "+r.state()+" plan="+r.planId()),false));return all.size();}))
            .then(Commands.literal("info").then(Commands.argument("id",UuidArgument.uuid()).executes(context->{UUID id=UuidArgument.getUuid(context,"id");WishExecutionRecord r=WishExecutionSavedData.get(context.getSource().getServer()).get(id);if(r==null){context.getSource().sendFailure(Component.literal("Execution not found."));return 0;}context.getSource().sendSuccess(()->Component.literal("execution="+r.executionId()+" plan="+r.planId()+" wish="+r.wishSessionId()+" owner="+r.ownerId()+" state="+r.state()+" error="+r.lastError()),false);for(WishStepExecution s:r.steps())context.getSource().sendSuccess(()->Component.literal("step="+s.stepIndex()+" state="+s.state()+" result="+s.lastResult()+" error="+s.lastError()),false);return 1;})))
            .then(Commands.literal("cancel").then(Commands.argument("id",UuidArgument.uuid()).executes(context->{boolean ok=WishExecutionManager.cancel(context.getSource().getServer(),UuidArgument.getUuid(context,"id"));if(!ok)context.getSource().sendFailure(Component.literal("Execution cannot be cancelled."));return ok?1:0;})))
            .then(Commands.literal("dryrun").then(Commands.argument("planId",UuidArgument.uuid()).executes(context->{UUID id=UuidArgument.getUuid(context,"planId");var plan=com.ikunkk02.wishingwillow.wish.WishSavedData.get(context.getSource().getServer()).allRecords().stream().map(com.ikunkk02.wishingwillow.wish.WishRecord::plan).filter(java.util.Objects::nonNull).filter(p->p.planId().equals(id)).findFirst().orElse(null);if(plan==null){context.getSource().sendFailure(Component.literal("Plan not found."));return 0;}var lines=WishExecutionManager.dryRun(context.getSource().getServer(),plan);lines.forEach(line->context.getSource().sendSuccess(()->Component.literal(line),false));return lines.stream().allMatch(line->line.endsWith("READY"))?1:0;})))
            .then(Commands.literal("trigger").requires(source->!FMLEnvironment.production).then(Commands.argument("id",UuidArgument.uuid()).then(Commands.argument("step",IntegerArgumentType.integer(0)).executes(context->{boolean ok=WishExecutionManager.debugTrigger(context.getSource().getServer(),UuidArgument.getUuid(context,"id"),IntegerArgumentType.getInteger(context,"step"));return ok?1:0;}))))));}
}
