package me.shinsunyoung.springbootdeveloper.ai;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PollinationsChatModel implements ChatModel {

    private static final String TEXT_URL = "https://text.pollinations.ai/";
    private static final int MAX_RETRY = 2;
    private static final Pattern WAIT_PATTERN = Pattern.compile("wait (\\d+) seconds");

    private final RestClient restClient = RestClient.create();

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Map<String, String>> messages = prompt.getInstructions().stream()
                .map(message -> Map.of(
                        "role", toRole(message.getMessageType()),
                        "content", message.getText() == null ? "" : message.getText()))
                .toList();

        Map<String, Object> body = Map.of("model", "openai", "messages", messages);

        String text = requestWithRetry(body);

        return new ChatResponse(List.of(new Generation(new AssistantMessage(text == null ? "" : text))));
    }

    private String requestWithRetry(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.post()
                        .uri(TEXT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                boolean rateLimited = status == 402 || status == 429;
                if (!rateLimited || attempt >= MAX_RETRY) {
                    throw e;
                }
                int waitSeconds = parseWaitSeconds(e.getResponseBodyAsString()) + 2;
                System.out.println("Pollinations 무료 제한, " + waitSeconds + "초 후 다시 시도합니다.");
                sleep(waitSeconds);
            }
        }
    }

    private int parseWaitSeconds(String message) {
        Matcher matcher = WAIT_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 30;
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 중단되었습니다.", e);
        }
    }

    private String toRole(MessageType type) {
        return switch (type) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            default -> "user";
        };
    }
}
