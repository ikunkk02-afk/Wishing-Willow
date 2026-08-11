package com.ikunkk02.wishingwillow.execution;

import net.minecraft.nbt.CompoundTag;

public final class WishStepExecution {
    private final int stepIndex;
    private WishStepExecutionState state;
    private long executeAtGameTime;
    private long targetDeadlineGameTime;
    private long startedGameTime;
    private int retryCount;
    private String lastError;
    private String lastResult;
    private int affected;

    public WishStepExecution(int stepIndex) {
        this(stepIndex, WishStepExecutionState.PENDING, -1, -1, -1, 0, "", "", 0);
    }

    private WishStepExecution(int stepIndex, WishStepExecutionState state, long executeAtGameTime,
                              long targetDeadlineGameTime, long startedGameTime, int retryCount, String lastError,
                              String lastResult, int affected) {
        this.stepIndex = stepIndex;
        this.state = state;
        this.executeAtGameTime = executeAtGameTime;
        this.targetDeadlineGameTime = targetDeadlineGameTime;
        this.startedGameTime = startedGameTime;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.lastResult = lastResult;
        this.affected = affected;
    }

    public int stepIndex() { return stepIndex; }
    public WishStepExecutionState state() { return state; }
    public long executeAtGameTime() { return executeAtGameTime; }
    public long targetDeadlineGameTime() { return targetDeadlineGameTime; }
    public long startedGameTime() { return startedGameTime; }
    public int retryCount() { return retryCount; }
    public String lastError() { return lastError; }
    public String lastResult() { return lastResult; }
    public int affected() { return affected; }

    public void transition(WishStepExecutionState next, long now) {
        if (state.terminal() && next != state) throw new IllegalStateException("TERMINAL_STEP");
        state = next;
        if (next == WishStepExecutionState.RUNNING && startedGameTime < 0) startedGameTime = now;
        if (next == WishStepExecutionState.WAITING_TARGET && targetDeadlineGameTime < 0) {
            targetDeadlineGameTime = now + 24_000L;
        }
    }
    public void schedule(long time) { executeAtGameTime = time; }
    public void retry(String error) { retryCount++; lastError = safe(error); }
    void recoverBlockBatch() {
        if (state == WishStepExecutionState.RUNNING) state = WishStepExecutionState.WAITING_DELAY;
    }
    void markStale() {
        if (state == WishStepExecutionState.RUNNING) state = WishStepExecutionState.STALE;
    }
    public void result(WishActionResult value) {
        lastResult = value.status().name(); lastError = safe(value.code()); affected = value.affected();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StepIndex", stepIndex); tag.putString("State", state.name());
        tag.putLong("ExecuteAt", executeAtGameTime); tag.putLong("TargetDeadline", targetDeadlineGameTime);
        tag.putLong("StartedGameTime", startedGameTime);
        tag.putInt("RetryCount", retryCount); tag.putString("LastError", lastError);
        tag.putString("LastResult", lastResult); tag.putInt("Affected", affected);
        return tag;
    }

    public static WishStepExecution load(CompoundTag tag) {
        WishStepExecutionState state;
        try { state = WishStepExecutionState.valueOf(tag.getString("State")); }
        catch (IllegalArgumentException ignored) { state = WishStepExecutionState.STALE; }
        return new WishStepExecution(tag.getInt("StepIndex"), state, tag.getLong("ExecuteAt"),
                tag.contains("TargetDeadline") ? tag.getLong("TargetDeadline") : -1,
                tag.contains("StartedGameTime") ? tag.getLong("StartedGameTime") : -1,
                tag.getInt("RetryCount"), tag.getString("LastError"), tag.getString("LastResult"),
                tag.getInt("Affected"));
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
