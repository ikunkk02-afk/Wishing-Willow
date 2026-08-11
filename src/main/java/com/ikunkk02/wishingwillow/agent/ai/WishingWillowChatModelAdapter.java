package com.ikunkk02.wishingwillow.agent.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.AiConversationMessage;
import com.ikunkk02.wishingwillow.ai.AiProvider;
import com.ikunkk02.wishingwillow.ai.AiToolCall;
import com.ikunkk02.wishingwillow.ai.AiToolDefinition;
import com.ikunkk02.wishingwillow.ai.AiToolRequest;
import com.ikunkk02.wishingwillow.ai.AiToolResponse;
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
import java.util.concurrent.atomic.AtomicLong;

/** LangChain4j model facade backed exclusively by Wishing Willow's existing provider. */
public final class WishingWillowChatModelAdapter implements ChatModel {
    private final AiProvider provider;
    private final int defaultMaxTokens;
    private final AtomicLong generatedCallIds = new AtomicLong();

    public WishingWillowChatModelAdapter(AiProvider provider, int defaultMaxTokens) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.defaultMaxTokens = Math.max(128, defaultMaxTokens);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        try {
            AiToolResponse response = provider.completeTools(new AiToolRequest(
                    request.messages().stream().map(this::message).toList(),
                    request.toolSpecifications().stream().map(this::tool).toList(),
                    request.maxOutputTokens() == null ? defaultMaxTokens : request.maxOutputTokens()
            )).join();
            List<ToolExecutionRequest> calls = response.toolCalls().stream().map(call ->
                    ToolExecutionRequest.builder().id(safeId(call.id())).name(call.name())
                            .arguments(call.argumentsJson()).build()).toList();
            return ChatResponse.builder().aiMessage(AiMessage.from(response.assistantContent(), calls)).build();
        } catch (CompletionException exception) {
            throw exception;
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
