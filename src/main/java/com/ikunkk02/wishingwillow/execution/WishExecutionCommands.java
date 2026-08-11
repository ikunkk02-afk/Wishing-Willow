package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Objects;
import java.util.UUID;
import com.ikunkk02.wishingwillow.agent.core.WishAgentDebugStore;

public final class WishExecutionCommands {
    private WishExecutionCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wishingwillow")
                .then(Commands.literal("agent")
                        .then(Commands.literal("latest").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var debug = WishAgentDebugStore.latest(player.getUUID());
                            if (debug == null) { context.getSource().sendFailure(Component.literal("No agent debug record found.")); return 0; }
                            context.getSource().sendSuccess(() -> Component.literal("session=" + debug.sessionId()
                                    + " mode=" + debug.mode() + " iterations=" + debug.iterations()
                                    + " toolCalls=" + debug.toolCalls() + " toolsUsed=" + debug.toolsUsed()
                                    + " verificationState=" + debug.verificationState()
                                    + " finalizationState=" + debug.finalizationState()), false);
                            return 1;
                        })))
                .then(Commands.literal("wish")
                        .then(Commands.literal("latest").executes(context -> {
                            var player=context.getSource().getPlayerOrException();
                            WishRecord wish=WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            if(wish==null){context.getSource().sendFailure(Component.literal("No wish record found."));return 0;}
                            context.getSource().sendSuccess(()->Component.literal(
                                    "session="+wish.sessionId()+
                                            " interpretationState="+wish.interpretationState()+
                                            " planState="+wish.planState()+
                                            " planError="+wish.planError()+
                                            " executionState="+wish.executionState()+
                                            " executionError="+wish.executionError()+
                                            " executionId="+wish.executionId()),false);
                            return 1;
                        })))
                .then(Commands.literal("pipeline").requires(source->source.hasPermission(2))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("sessionId",UuidArgument.uuid()).executes(context -> {
                                    UUID session=UuidArgument.getUuid(context,"sessionId");
                                    WishRecord wish=WishSavedData.get(context.getSource().getServer()).getBySession(session);
                                    if(wish==null){context.getSource().sendFailure(Component.literal("Wish session not found."));return 0;}
                                    context.getSource().sendSuccess(()->Component.literal(
                                            "session="+wish.sessionId()+" owner="+wish.playerId()+
                                                    " interpretationState="+wish.interpretationState()+
                                                    " planState="+wish.planState()+" planError="+wish.planError()+
                                                    " planId="+(wish.plan()==null?null:wish.plan().planId())+
                                                    " executionState="+wish.executionState()+
                                                    " executionError="+wish.executionError()+
                                                    " executionDetail="+wish.executionErrorDetail()+
                                                    " executionId="+wish.executionId()),false);
                                    WishExecutionRecord execution=wish.executionId()==null?null:
                                            WishExecutionSavedData.get(context.getSource().getServer()).get(wish.executionId());
                                    if(execution!=null)for(WishStepExecution step:execution.steps())
                                        context.getSource().sendSuccess(()->Component.literal(
                                                "step="+step.stepIndex()+" state="+step.state()+
                                                        " result="+step.lastResult()+" error="+step.lastError()),false);
                                    return 1;
                                }))))
                .then(Commands.literal("execution").requires(source->source.hasPermission(2))
                        .then(Commands.literal("list").executes(context -> {
                            var all=WishExecutionSavedData.get(context.getSource().getServer()).all();
                            context.getSource().sendSuccess(()->Component.literal("Wish executions: "+all.size()),false);
                            all.stream().filter(record->!record.state().terminal()).limit(20).forEach(record->
                                    context.getSource().sendSuccess(()->Component.literal(
                                            record.executionId()+" "+record.state()+" plan="+record.planId()),false));
                            return all.size();
                        }))
                        .then(Commands.literal("info").then(Commands.argument("id",UuidArgument.uuid()).executes(context -> {
                            UUID id=UuidArgument.getUuid(context,"id");
                            WishExecutionRecord record=WishExecutionSavedData.get(context.getSource().getServer()).get(id);
                            if(record==null){context.getSource().sendFailure(Component.literal("Execution not found."));return 0;}
                            context.getSource().sendSuccess(()->Component.literal(
                                    "execution="+record.executionId()+" plan="+record.planId()+
                                            " wish="+record.wishSessionId()+" owner="+record.ownerId()+
                                            " state="+record.state()+" error="+record.lastError()),false);
                            for(WishStepExecution step:record.steps())context.getSource().sendSuccess(()->Component.literal(
                                    "step="+step.stepIndex()+" state="+step.state()+
                                            " result="+step.lastResult()+" error="+step.lastError()),false);
                            return 1;
                        })))
                        .then(Commands.literal("cancel").then(Commands.argument("id",UuidArgument.uuid()).executes(context -> {
                            boolean ok=WishExecutionManager.cancel(context.getSource().getServer(),UuidArgument.getUuid(context,"id"));
                            if(!ok)context.getSource().sendFailure(Component.literal("Execution cannot be cancelled."));
                            return ok?1:0;
                        })))
                        .then(Commands.literal("dryrun").then(Commands.argument("planId",UuidArgument.uuid()).executes(context -> {
                            UUID id=UuidArgument.getUuid(context,"planId");
                            var plan=WishSavedData.get(context.getSource().getServer()).allRecords().stream()
                                    .map(WishRecord::plan).filter(Objects::nonNull).filter(value->value.planId().equals(id))
                                    .findFirst().orElse(null);
                            if(plan==null){context.getSource().sendFailure(Component.literal("Plan not found."));return 0;}
                            var lines=WishExecutionManager.dryRun(context.getSource().getServer(),plan);
                            lines.forEach(line->context.getSource().sendSuccess(()->Component.literal(line),false));
                            return lines.stream().allMatch(line->line.endsWith("READY"))?1:0;
                        })))
                        .then(Commands.literal("trigger").requires(source->!FMLEnvironment.production)
                                .then(Commands.argument("id",UuidArgument.uuid())
                                        .then(Commands.argument("step",IntegerArgumentType.integer(0)).executes(context ->
                                                WishExecutionManager.debugTrigger(context.getSource().getServer(),
                                                        UuidArgument.getUuid(context,"id"),
                                                        IntegerArgumentType.getInteger(context,"step"))?1:0))))));
    }
}
