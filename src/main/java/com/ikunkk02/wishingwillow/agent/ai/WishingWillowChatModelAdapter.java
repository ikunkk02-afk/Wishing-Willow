package com.ikunkk02.wishingwillow.agent.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.AiConversationMessage;
import com.ikunkk02.wishingwillow.ai.AiProvider;
import com.ikunkk02.wishingwillow.ai.AiToolCall;
import com.ikunkk02.wishingwillow.ai.AiToolDefinition;
import com.ikunkk02.wishingwillow.ai.AiToolRequest;
import com.ikunkk02.wishingwillow.ai.AiToolResponse;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiRequestException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** LangChain4j model facade backed exclusively by Wishing Willow's existing provider. */
public final class WishingWillowChatModelAdapter implements ChatModel {
    public static final Duration DEFAULT_AGENT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final AiProvider provider;
    private final int defaultMaxTokens;
    private final Duration requestTimeout;
    private final LongSupplier remainingMillis;
    private final BooleanSupplier cancelled;
    private final AtomicLong generatedCallIds = new AtomicLong();

    public WishingWillowChatModelAdapter(AiProvider provider, int defaultMaxTokens) {
        this(provider, defaultMaxTokens, DEFAULT_AGENT_REQUEST_TIMEOUT,
                DEFAULT_AGENT_REQUEST_TIMEOUT::toMillis, () -> false);
    }

    public WishingWillowChatModelAdapter(AiProvider provider, int defaultMaxTokens,
                                         Duration requestTimeout, LongSupplier remainingMillis,
                                         BooleanSupplier cancelled) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.defaultMaxTokens = Math.max(128, defaultMaxTokens);
        this.requestTimeout = java.util.Objects.requireNonNull(requestTimeout);
        this.remainingMillis = java.util.Objects.requireNonNull(remainingMillis);
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        java.util.concurrent.CompletableFuture<AiToolResponse> pending = null;
        try {
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("AGENT_CANCELLED");
            pending = provider.completeTools(new AiToolRequest(
                    request.messages().stream().map(this::message).toList(),
                    request.toolSpecifications().stream().map(this::tool).toList(),
                    request.maxOutputTokens() == null ? defaultMaxTokens : request.maxOutputTokens()
            ));
            long timeoutMillis = Math.max(1L, Math.min(requestTimeout.toMillis(), remainingMillis.getAsLong()));
            AiToolResponse response = pending.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("AGENT_CANCELLED");
            List<ToolExecutionRequest> calls = response.toolCalls().stream().map(call ->
                    ToolExecutionRequest.builder().id(safeId(call.id())).name(call.name())
                            .arguments(call.argumentsJson()).build()).toList();
            return ChatResponse.builder().aiMessage(AiMessage.from(response.assistantContent(), calls)).build();
        } catch (TimeoutException exception) {
            if (pending != null) pending.cancel(true);
            throw new CompletionException(new AiRequestException(AiErrorCategory.TIMEOUT, exception));
        } catch (InterruptedException exception) {
            if (pending != null) pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new CompletionException(new AiRequestException(AiErrorCategory.TIMEOUT, exception));
        } catch (ExecutionException exception) {
            throw new CompletionException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw new CompletionException(exception);
        }
    }

    private AiConversationMessage message(ChatMessage message) {
        if (message instanceof SystemMessage system) return AiConversationMessage.text("system", system.text());
        if (message instanceof UserMessage user) {
            if (!user.hasSingleText()) throw new IllegalArgumentException("MULTIMODAL_MESSAGE_UNSUPPORTED");
            return AiConversationMessage.text("user", user.singleText());
        }
        if (message instanceof AiMessage assistant) {
            List<AiToolCall> calls = assistant.toolExecutionRequests().stream()
                    .map(call -> new AiToolCall(safeId(call.id()), call.name(), call.arguments())).toList();
            return AiConversationMessage.assistant(assistant.text(), calls);
        }
        if (message instanceof ToolExecutionResultMessage result) {
            if (!result.hasSingleText()) throw new IllegalArgumentException("MULTIMODAL_TOOL_RESULT_UNSUPPORTED");
            return AiConversationMessage.tool(safeId(result.id()), result.text());
        }
        throw new IllegalArgumentException("UNSUPPORTED_MESSAGE_TYPE");
    }

    private AiToolDefinition tool(ToolSpecification specification) {
        JsonObject parameters = new JsonObject();
        try {
            JsonObject json = JsonParser.parseString(specification.toJson()).getAsJsonObject();
            if (json.has("parameters") && json.get("parameters").isJsonObject()) {
                parameters = json.getAsJsonObject("parameters");
            }
        } catch (RuntimeException ignored) {
            // The existing provider still receives a valid empty object schema.
        }
        return new AiToolDefinition(specification.name(), specification.description(), parameters);
    }

    private String safeId(String id) {
        return id == null || id.isBlank() ? "ww-call-" + generatedCallIds.incrementAndGet() : id;
    }
}
