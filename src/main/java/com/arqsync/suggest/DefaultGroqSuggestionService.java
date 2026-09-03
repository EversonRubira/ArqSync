package com.arqsync.suggest;

import com.arqsync.analyzer.AdapterSemPortaViolation;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.Cycle;
import com.arqsync.analyzer.LayerViolation;
import com.arqsync.analyzer.PackageName;
import com.arqsync.scanner.ProjectScan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls the Groq chat completions API (OpenAI-compatible) with a structured
 * summary of one {@code AnalysisResult} — metrics, cycles, violations and
 * the detected architectural style, never source code — and asks it to
 * suggest (never perform) architectural improvements.
 *
 * <p>Configuration is read from the environment: {@code GROQ_API_KEY}
 * (required — missing key logs a warning and returns no suggestions,
 * without making a network call) and {@code GROQ_MODEL} (optional, default
 * {@value #DEFAULT_MODEL}).
 *
 * <p>Resilience: one retry with a fixed backoff on transport failure or a
 * non-2xx/non-401 status; a 401 (invalid key) is never retried. Any failure
 * that survives the retry is logged as a warning and yields an empty list —
 * {@code --suggest} is always optional and never fails the pipeline.
 */
@Service
public class DefaultGroqSuggestionService implements GroqSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultGroqSuggestionService.class);

    static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_ITEMS_PER_LIST = 15;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            Você é um consultor de arquitetura de software especializado em projetos Java. \
            Você recebe um resumo estruturado (JSON) da análise arquitetural de um projeto - \
            estilo arquitetural detectado, ciclos de dependência entre pacotes, violações de \
            camada, violações de adapter sem porta (Arquitetura Hexagonal) e métricas gerais. \
            Nenhum código-fonte é enviado a você.

            Com base apenas nesse resumo, sugira:
            1. Melhorias arquiteturais (ex.: migrar de um estilo para outro mais adequado)
            2. Como quebrar cada ciclo de dependência listado
            3. Como corrigir cada violação de camada listada
            4. Como corrigir cada violação de adapter sem porta listada (definir uma \
            interface no núcleo do domínio e fazer o adapter implementá-la)
            5. Um exemplo de código ilustrando a refatoração sugerida, quando fizer sentido

            Você NUNCA deve executar nem aplicar nenhuma refatoração - apenas sugerir. Suas \
            sugestões são conselhos para um humano avaliar e aplicar manualmente.

            Responda APENAS com um objeto JSON válido, sem nenhum texto fora dele, exatamente \
            neste formato:
            {"suggestions": [{"type": "CYCLE_BREAK|LAYER_VIOLATION|STYLE_MIGRATION|GENERAL", \
            "title": "título curto", "description": "explicação da sugestão", \
            "codeExample": "trecho de código ou null se não aplicável"}]}""";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public DefaultGroqSuggestionService() {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                new ObjectMapper(),
                System.getenv("GROQ_API_KEY"),
                System.getenv("GROQ_MODEL"));
    }

    /**
     * Visible for tests, to inject a fake {@link HttpClient} and fixed
     * env-var values without touching the real environment.
     */
    DefaultGroqSuggestionService(HttpClient httpClient, ObjectMapper objectMapper, String apiKey, String model) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    @Override
    public List<AiSuggestion> suggest(ProjectScan projectScan, AnalysisResult analysisResult) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY não configurada - prosseguindo sem sugestões de IA");
            return List.of();
        }

        HttpRequest request = buildRequest(buildStructuredSummary(analysisResult));
        HttpResponse<String> response = sendWithRetry(request);
        if (response == null) {
            return List.of();
        }

        List<AiSuggestion> suggestions = new ArrayList<>(parseSuggestions(response.body()));
        suggestions.sort(Comparator.comparingInt(s -> s.type().ordinal()));
        return suggestions;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response;
                }
                if (response.statusCode() == 401) {
                    log.error("[ERROR] GROQ_API_KEY inválida. Verifique sua configuração.");
                    return null;
                }
                log.warn("Groq API respondeu com status {} (tentativa {}/2): {}",
                        response.statusCode(), attempt, extractErrorMessage(response.body()));
            } catch (IOException e) {
                // e is deliberately passed twice: SLF4J always strips a trailing Throwable
                // argument out of message substitution (regardless of placeholder count) to
                // attach it as the log event's stack trace, so a second, string-typed copy is
                // needed to actually get the exception type/message into the message text.
                log.warn("Falha ao chamar a API Groq (tentativa {}/2): {}", attempt, e.toString(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[WARN] Groq API indisponível, prosseguindo sem sugestões (interrompido)");
                return null;
            }

            if (attempt == 1) {
                sleepBackoff();
            }
        }
        log.warn("[WARN] Groq API indisponível, prosseguindo sem sugestões");
        return null;
    }

    /**
     * Pulls {@code error.code}/{@code error.message} out of a Groq error body
     * (e.g. {@code model_not_found}, {@code invalid_api_key}) so the log
     * shows the real cause instead of just a status code. Falls back to a
     * truncated raw body when it isn't the expected shape.
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "(corpo vazio)";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String code = error.path("code").asText(null);
            String message = error.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return (code != null && !code.isBlank()) ? code + ": " + message : message;
            }
        } catch (IOException e) {
            // Body isn't JSON - fall through to the raw (truncated) body below.
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest buildRequest(String structuredSummary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.3);
        root.putObject("response_format").put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", structuredSummary);

        String body;
        try {
            body = objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Groq request body", e);
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
     * Builds the "resumo estruturado" sent as context: architectural style,
     * headline metrics, and up to {@value #MAX_ITEMS_PER_LIST} cycles,
     * layer violations and adapter-sem-porta violations each — no class
     * names beyond the offending adapter's own (still no source code).
     * Bounding the list sizes keeps the prompt (and token cost) predictable
     * regardless of project size.
     */
    private String buildStructuredSummary(AnalysisResult analysisResult) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("architectureStyle", analysisResult.architectureStyle().name());
        summary.put("architectureStyleDescription", analysisResult.architectureStyle().description());

        ObjectNode metrics = summary.putObject("metrics");
        metrics.put("totalPackages", analysisResult.metrics().totalPackages());
        metrics.put("totalClasses", analysisResult.metrics().totalClasses());
        metrics.put("cycleCount", analysisResult.metrics().cycleCount());
        metrics.put("violationCount", analysisResult.metrics().violationCount());

        ArrayNode cyclesNode = summary.putArray("cycles");
        for (Cycle cycle : capped(analysisResult.cycles())) {
            ArrayNode path = cyclesNode.addArray();
            for (PackageName pkg : cycle.path()) {
                path.add(pkg.value());
            }
        }

        ArrayNode violationsNode = summary.putArray("violations");
        for (LayerViolation violation : capped(analysisResult.violations())) {
            ObjectNode v = violationsNode.addObject();
            v.put("from", violation.from().value());
            v.put("to", violation.to().value());
            v.put("fromLayer", violation.fromLayer().name());
            v.put("toLayer", violation.toLayer().name());
            v.put("type", violation.type().name());
        }

        ArrayNode adapterPortViolationsNode = summary.putArray("adapterPortViolations");
        for (AdapterSemPortaViolation violation : capped(analysisResult.adapterPortViolations())) {
            ObjectNode v = adapterPortViolationsNode.addObject();
            v.put("adapterPackage", violation.adapterPackage().value());
            v.put("className", violation.className());
        }

        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize architecture summary", e);
        }
    }

    private <T> List<T> capped(List<T> items) {
        return items.size() > MAX_ITEMS_PER_LIST ? items.subList(0, MAX_ITEMS_PER_LIST) : items;
    }

    /**
     * Reads {@code choices[0].message.content} from the chat completion
     * response and parses it as {@code {"suggestions": [...]}}. Tolerant of
     * the content being wrapped in extra text (e.g. markdown fences) even
     * though {@code response_format: json_object} is requested - falls back
     * to extracting the first {@code {...}} block via regex before giving up.
     */
    private List<AiSuggestion> parseSuggestions(String responseBody) {
        String content;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            content = root.path("choices").path(0).path("message").path("content").asText(null);
        } catch (IOException e) {
            log.warn("[WARN] Resposta da API Groq em formato inesperado, prosseguindo sem sugestões");
            return List.of();
        }

        if (content == null || content.isBlank()) {
            log.warn("[WARN] Resposta da API Groq vazia, prosseguindo sem sugestões");
            return List.of();
        }

        JsonNode suggestionsNode = extractSuggestionsArray(content);
        if (suggestionsNode == null) {
            log.warn("[WARN] Resposta da IA em formato inesperado, prosseguindo sem sugestões");
            return List.of();
        }

        List<AiSuggestion> suggestions = new ArrayList<>();
        for (JsonNode node : suggestionsNode) {
            AiSuggestion suggestion = toSuggestion(node);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    private JsonNode extractSuggestionsArray(String content) {
        JsonNode parsed = tryParse(content);
        if (parsed == null) {
            Matcher matcher = JSON_BLOCK.matcher(content);
            if (matcher.find()) {
                parsed = tryParse(matcher.group());
            }
        }
        if (parsed == null) {
            return null;
        }
        JsonNode suggestions = parsed.path("suggestions");
        return suggestions.isArray() ? suggestions : null;
    }

    private JsonNode tryParse(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            return null;
        }
    }

    private AiSuggestion toSuggestion(JsonNode node) {
        SuggestionType type;
        try {
            type = SuggestionType.valueOf(node.path("type").asText("GENERAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = SuggestionType.GENERAL;
        }
        String title = node.path("title").asText("");
        String description = node.path("description").asText("");
        if (title.isBlank() && description.isBlank()) {
            return null;
        }
        String codeExample = node.hasNonNull("codeExample") ? node.path("codeExample").asText() : null;
        return new AiSuggestion(type, title, description, codeExample);
    }
}
