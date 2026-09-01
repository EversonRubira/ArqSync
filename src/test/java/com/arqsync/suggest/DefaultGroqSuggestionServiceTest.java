package com.arqsync.suggest;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.Cycle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.analyzer.Layer;
import com.arqsync.analyzer.LayerViolation;
import com.arqsync.analyzer.PackageName;
import com.arqsync.analyzer.ViolationType;
import com.arqsync.scanner.ProjectScan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultGroqSuggestionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProjectScan projectScan() {
        return new ProjectScan("/repo/my-project", List.of(), List.of());
    }

    private AnalysisResult analysisResultWithCycleAndViolation() {
        List<Cycle> cycles = List.of(new Cycle(
                List.of(new PackageName("com.acme.a"), new PackageName("com.acme.b"), new PackageName("com.acme.a")),
                "explicação", "sugestão"
        ));
        List<LayerViolation> violations = List.of(new LayerViolation(
                new PackageName("com.acme.controller"), new PackageName("com.acme.repository"),
                Layer.CONTROLLER, Layer.REPOSITORY, ViolationType.LAYER_SKIP,
                List.of(), "explicação", "sugestão"
        ));
        return new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                cycles, violations, List.of(),
                new AnalysisMetrics(2, 5, 1, 1, List.of()),
                new ArchitectureStyle("Arquitetura em Camadas (Layered)", "descrição")
        );
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private String chatCompletionBody(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + toJsonString(content) + "}}]}";
    }

    private String toJsonString(String raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void missingApiKeyReturnsEmptyListWithoutCallingHttpClient() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, null, null);

        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void blankApiKeyReturnsEmptyListWithoutCallingHttpClient() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "  ", null);

        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void successfulResponseIsParsedAndSortedByFixedSeverity() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        String content = "{\"suggestions\":["
                + "{\"type\":\"GENERAL\",\"title\":\"Geral\",\"description\":\"d\"},"
                + "{\"type\":\"CYCLE_BREAK\",\"title\":\"Quebre o ciclo\",\"description\":\"d\",\"codeExample\":\"interface X {}\"},"
                + "{\"type\":\"LAYER_VIOLATION\",\"title\":\"Corrija a violação\",\"description\":\"d\"}"
                + "]}";
        doReturn(mockResponse(200, chatCompletionBody(content))).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).type()).isEqualTo(SuggestionType.CYCLE_BREAK);
        assertThat(result.get(0).codeExample()).isEqualTo("interface X {}");
        assertThat(result.get(1).type()).isEqualTo(SuggestionType.LAYER_VIOLATION);
        assertThat(result.get(2).type()).isEqualTo(SuggestionType.GENERAL);
    }

    @Test
    void invalidApiKeyDoesNotRetryAndReturnsEmptyList() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        doReturn(mockResponse(401, "")).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "bad-key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void transientFailureIsRetriedOnceAndSucceeds() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        String content = "{\"suggestions\":[{\"type\":\"GENERAL\",\"title\":\"t\",\"description\":\"d\"}]}";
        HttpResponse<String> failureResponse = mockResponse(500, "server error");
        HttpResponse<String> successResponse = mockResponse(200, chatCompletionBody(content));
        doReturn(failureResponse).doReturn(successResponse).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).hasSize(1);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void persistentFailureReturnsEmptyListAfterOneRetry() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        doReturn(mockResponse(500, "server error")).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void transportExceptionIsRetriedThenReturnsEmptyList() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        doThrow(new IOException("connection refused")).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void contentWrappedInExtraTextIsToleratedViaJsonExtraction() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        String content = "Aqui está minha resposta:\n```json\n"
                + "{\"suggestions\":[{\"type\":\"STYLE_MIGRATION\",\"title\":\"t\",\"description\":\"d\"}]}"
                + "\n```";
        doReturn(mockResponse(200, chatCompletionBody(content))).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(SuggestionType.STYLE_MIGRATION);
    }

    @Test
    void unparsableContentReturnsEmptyList() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        doReturn(mockResponse(200, chatCompletionBody("not json at all"))).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).isEmpty();
    }

    @Test
    void unknownSuggestionTypeFallsBackToGeneral() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        String content = "{\"suggestions\":[{\"type\":\"SOMETHING_UNEXPECTED\",\"title\":\"t\",\"description\":\"d\"}]}";
        doReturn(mockResponse(200, chatCompletionBody(content))).when(httpClient).send(any(), any());

        GroqSuggestionService service = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        List<AiSuggestion> result = service.suggest(projectScan(), analysisResultWithCycleAndViolation());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(SuggestionType.GENERAL);
    }

    @Test
    void requestBodyUsesConfiguredModelAndDefaultsWhenNotConfigured() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        doReturn(mockResponse(200, chatCompletionBody("{\"suggestions\":[]}"))).when(httpClient).send(any(), any());

        GroqSuggestionService withCustomModel =
                new DefaultGroqSuggestionService(httpClient, objectMapper, "key", "custom-model");
        withCustomModel.suggest(projectScan(), analysisResultWithCycleAndViolation());

        GroqSuggestionService withDefaultModel = new DefaultGroqSuggestionService(httpClient, objectMapper, "key", null);
        withDefaultModel.suggest(projectScan(), analysisResultWithCycleAndViolation());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        List<HttpRequest> requests = captor.getAllValues();
        assertThat(bodyOf(requests.get(0))).contains("\"model\":\"custom-model\"");
        assertThat(bodyOf(requests.get(1))).contains("\"model\":\"" + DefaultGroqSuggestionService.DEFAULT_MODEL + "\"");
    }

    private String bodyOf(HttpRequest request) {
        return request.bodyPublisher()
                .map(publisher -> {
                    StringBuilder sb = new StringBuilder();
                    publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        public void onNext(java.nio.ByteBuffer item) {
                            byte[] bytes = new byte[item.remaining()];
                            item.get(bytes);
                            sb.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                        }

                        public void onError(Throwable throwable) {
                        }

                        public void onComplete() {
                        }
                    });
                    return sb.toString();
                })
                .orElse("");
    }
}
