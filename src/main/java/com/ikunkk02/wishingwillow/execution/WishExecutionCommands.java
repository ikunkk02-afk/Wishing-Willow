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
                            WishRecord latest = WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            String executionState = latest != null && latest.sessionId().equals(debug.sessionId())
                                    ? latest.executionState().name() : "UNKNOWN";
                            context.getSource().sendSuccess(() -> Component.literal("session=" + debug.sessionId()
                                    + " route=" + debug.route() + " route_reason=" + debug.routeReason()
                                    + " core_outcome=" + debug.coreOutcome()
                                    + " absurdity_style=" + debug.absurdityStyle()
                                    + " absurdity_intensity=" + debug.absurdityIntensity()
                                    + " direct_actions=" + debug.directActions()
                                    + " agent_tools=" + (debug.route() == com.ikunkk02.wishingwillow.planning.WishExecutionRoute.DIRECT_ACTION
                                    ? "not used" : debug.toolsUsed())
                                    + " validation_state=" + debug.verificationState()
                                    + " execution_state=" + executionState
                                    + " mode=" + debug.mode() + " state=" + debug.state()
                                    + " iterations=" + debug.iterations()
                                    + " toolCalls=" + debug.toolCalls() + " toolsUsed=" + debug.toolsUsed()
                                    + " lastTool=" + debug.lastTool() + " lastToolStatus=" + debug.lastToolStatus()
                                    + " verificationState=" + debug.verificationState()
                                    + " finalizationState=" + debug.finalizationState()
                                    + " fallback=" + debug.fallbackReason()
                                    + " elapsedMs=" + debug.elapsedMs()), false);
                            return 1;
                        })))
                .then(Commands.literal("program")
                        .then(Commands.literal("latest").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            WishRecord wish = WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            if (wish == null || wish.program() == null) {
                                context.getSource().sendFailure(Component.literal("No Wish Program record found.")); return 0;
                            }
                            WishExecutionRecord execution = wish.executionId() == null ? null
                                    : WishExecutionSavedData.get(context.getSource().getServer()).get(wish.executionId());
                            long elapsed = execution == null ? 0L
                                    : Math.max(0L, execution.updatedGameTime() - execution.createdGameTime()) * 50L;
                            int core = execution == null ? 0 : Math.min(execution.coreActionCount(), execution.steps().size());
                            long completedCore = execution == null ? 0 : execution.steps().stream()
                                    .filter(step -> step.stepIndex() < core
                                            && step.state() == WishStepExecutionState.SUCCEEDED).count();
                            long failedCore = execution == null ? 0 : execution.steps().stream()
                                    .filter(step -> step.stepIndex() < core
                                            && (step.state() == WishStepExecutionState.FAILED
                                            || step.state() == WishStepExecutionState.STALE)).count();
                            long completedPresentation = execution == null ? 0 : execution.steps().stream()
                                    .filter(step -> step.stepIndex() >= core
                                            && step.state() == WishStepExecutionState.SUCCEEDED).count();
                            long failedPresentation = execution == null ? 0 : execution.steps().stream()
                                    .filter(step -> step.stepIndex() >= core
                                            && (step.state() == WishStepExecutionState.FAILED
                                            || step.state() == WishStepExecutionState.STALE)).count();
                            WishStepExecution current = execution == null ? null : execution.steps().stream()
                                    .filter(step -> !step.state().terminal()).findFirst().orElse(null);
                            String currentAction = current == null ? "none"
                                    : execution.steps().isEmpty() ? "none"
                                    : (current.stepIndex() < core ? "CORE:" : "PRESENTATION:")
                                    + current.stepIndex();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "source=" + (execution == null ? "UNKNOWN" : execution.source().name())
                                    + " session=" + wish.sessionId()
                                    + " programId=" + (execution == null ? "none" : execution.planId())
                                    + " executionId=" + (execution == null ? "none" : wish.executionId())
                                    + " goal=" + wish.program().goal()
                                    + " state=" + wish.executionState()
                                    + " currentAction=" + currentAction
                                    + " completedCore=" + completedCore + " failedCore=" + failedCore
                                    + " completedPresentation=" + completedPresentation
                                    + " failedPresentation=" + failedPresentation
                                    + " coreActions=" + wish.program().coreActions().stream().map(a -> a.action()).toList()
                                    + " presentationActions=" + wish.program().presentationActions().stream().map(a -> a.action()).toList()
                                    + " agentUsed=" + wish.program().requiresAgent()
                                    + " legacyPlanUsed=" + (execution != null
                                    && execution.source() == ExecutionSource.LEGACY_WISH_PLAN)
                                    + " elapsedMs=" + elapsed), false);
                            return 1;
                        })))
                .then(Commands.literal("action")
                        .then(Commands.literal("latest").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            WishRecord wish = WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            WishExecutionRecord execution = wish == null || wish.executionId() == null ? null
                                    : WishExecutionSavedData.get(context.getSource().getServer()).get(wish.executionId());
                            if (wish == null || wish.plan() == null || execution == null) {
                                context.getSource().sendFailure(Component.literal("No action execution record found.")); return 0;
                            }
                            WishStepExecution step = execution.steps().stream().filter(value -> !value.state().terminal())
                                    .findFirst().orElseGet(() -> execution.steps().isEmpty() ? null
                                            : execution.steps().get(execution.steps().size() - 1));
                            if (step == null) return 0;
                            var planned = wish.plan().steps().get(step.stepIndex());
                            var definition = WishExecutionManager.actions().definition(planned.action());
                            context.getSource().sendSuccess(() -> Component.literal("session=" + wish.sessionId()
                                    + " action=" + (definition == null ? planned.action().name() : definition.id())
                                    + " parameters=" + planned.parameters() + " state=" + step.state()
                                    + " result=" + step.lastResult() + " error=" + step.lastError()
                                    + " affected=" + step.affected()), false);
                            return 1;
                        })))
                .then(Commands.literal("wish")
                        .then(Commands.literal("latest").executes(context -> {
                            var player=context.getSource().getPlayerOrException();
                            WishRecord wish=WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            if(wish==null){context.getSource().sendFailure(Component.literal("No wish record found."));return 0;}
                            var debug=WishAgentDebugStore.latest(player.getUUID());
                            context.getSource().sendSuccess(()->Component.literal(
                                    "session="+wish.sessionId()+
                                            (debug!=null&&debug.sessionId().equals(wish.sessionId())
                                                    ? " route="+debug.route()+" core_outcome="+debug.coreOutcome()
                                                    +" absurdity_style="+debug.absurdityStyle()
                                                    +" absurdity_intensity="+debug.absurdityIntensity()
                                                    +" direct_actions="+debug.directActions()
                                                    +" agent_tools="+(debug.route()==com.ikunkk02.wishingwillow.planning.WishExecutionRoute.DIRECT_ACTION
                                                    ?"not used":debug.toolsUsed())
                                                    +" validation_state="+debug.verificationState() : "")+
                                            " interpretationState="+wish.interpretationState()+
                                            " planState="+wish.planState()+
                                            " planError="+wish.planError()+
                                            " execution_state="+wish.executionState()+
                                            " executionError="+wish.executionError()+
                                            " executionId="+wish.executionId()+
                                            " steps="+(wish.plan()==null?0:wish.plan().steps().size())+
                                            (debug!=null&&debug.sessionId().equals(wish.sessionId())
                                                    ? " planningMode="+debug.mode()+" planningDebugState="+debug.state()
                                                    +" fallback="+debug.fallbackReason()+" elapsedMs="+debug.elapsedMs() : "")),false);
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
                .then(Commands.literal("execution")
                        .then(Commands.literal("latest").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            WishRecord wish = WishSavedData.get(context.getSource().getServer()).getLatest(player.getUUID());
                            WishExecutionRecord execution = wish == null || wish.executionId() == null ? null
                                    : WishExecutionSavedData.get(context.getSource().getServer()).get(wish.executionId());
                            if (execution == null) {
                                context.getSource().sendFailure(Component.literal("No execution record found.")); return 0;
                            }
                            long elapsed = Math.max(0L, execution.updatedGameTime() - execution.createdGameTime()) * 50L;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "execution=" + execution.executionId()
                                    + " source=" + execution.source()
                                    + " session=" + execution.wishSessionId()
                                    + " owner=" + execution.ownerId()
                                    + " state=" + execution.state()
                                    + " steps=" + execution.steps().size()
                                    + " coreActions=" + execution.coreActionCount()
                                    + " error=" + execution.lastError()
                                    + " elapsedMs=" + elapsed), false);
                            return 1;
                        }))
                        .then(Commands.literal("list").requires(source->source.hasPermission(2)).executes(context -> {
                            var all=WishExecutionSavedData.get(context.getSource().getServer()).all();
                            context.getSource().sendSuccess(()->Component.literal("Wish executions: "+all.size()),false);
                            all.stream().filter(record->!record.state().terminal()).limit(20).forEach(record->
                                    context.getSource().sendSuccess(()->Component.literal(
                                            record.executionId()+" "+record.state()+" plan="+record.planId()),false));
                            return all.size();
                        }))
                        .then(Commands.literal("info").requires(source->source.hasPermission(2)).then(Commands.argument("id",UuidArgument.uuid()).executes(context -> {
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
                        .then(Commands.literal("cancel").requires(source->source.hasPermission(2)).then(Commands.argument("id",UuidArgument.uuid()).executes(context -> {
                            boolean ok=WishExecutionManager.cancel(context.getSource().getServer(),UuidArgument.getUuid(context,"id"));
                            if(!ok)context.getSource().sendFailure(Component.literal("Execution cannot be cancelled."));
                            return ok?1:0;
                        })))
                        .then(Commands.literal("dryrun").requires(source->source.hasPermission(2)).then(Commands.argument("planId",UuidArgument.uuid()).executes(context -> {
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
