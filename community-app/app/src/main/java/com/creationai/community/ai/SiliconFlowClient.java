package com.creationai.community.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.creationai.community.news.NewsItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SiliconFlowClient {
    private static final String DEFAULT_ENDPOINT = "https://api.siliconflow.cn/v1/chat/completions";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private final String endpoint;

    public SiliconFlowClient() {
        this(DEFAULT_ENDPOINT);
    }

    public SiliconFlowClient(String endpoint) {
        this.endpoint = endpoint == null || endpoint.trim().isEmpty()
                ? DEFAULT_ENDPOINT : endpoint.trim();
    }

    public String rewriteTitle(String apiKey, String model, String originalTitle) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException("请先填写模型 API Key");
        if (model == null || model.trim().isEmpty()) throw new IOException("请填写模型名称");
        if (originalTitle == null || originalTitle.trim().isEmpty()) throw new IOException("请填写原始标题");

        JSONObject request = new JSONObject();
        try {
            request.put("model", model.trim());
            request.put("stream", false);
            request.put("max_tokens", 96);
            request.put("temperature", 0.45);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是短视频标题编辑。保留事实和原意，不捏造、不夸大、不使用误导性承诺；输出一个自然流畅的中文标题，不超过30个汉字，只输出标题。"));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "请改写：" + originalTitle.trim()));
            request.put("messages", messages);
        } catch (JSONException impossible) {
            throw new IOException("无法构造模型请求", impossible);
        }

        String body = post(apiKey, request);

        try {
            return cleanTitle(extractContent(body));
        } catch (JSONException e) {
            throw new IOException("模型响应格式无法解析", e);
        }
    }

    public OriginalContentPackage generateOriginalContent(String apiKey, String model,
                                                           String topic, String tags,
                                                           java.util.List<NewsItem> sources)
            throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException("请先填写模型 API Key");
        if (model == null || model.trim().isEmpty()) throw new IOException("请填写模型名称");
        if (topic == null || topic.trim().isEmpty()) throw new IOException("请填写原创主题");
        if (sources == null || sources.isEmpty()) throw new IOException("没有可用于创作的资讯来源");

        JSONObject request = new JSONObject();
        try {
            JSONArray sourceValues = new JSONArray();
            for (NewsItem source : sources) sourceValues.put(source.toJson());
            request.put("model", model.trim());
            request.put("stream", false);
            request.put("max_tokens", 4096);
            request.put("temperature", 0.35);
            request.put("response_format", new JSONObject().put("type", "json_object"));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是严谨的中文资讯编辑和短视频编导。只能使用用户提供的来源材料，不得补造数字、引语、机构结论或事件。信息冲突时明确写出尚待核实。输出必须是单个JSON对象，字段为title、article、video_script、cover_prompt、shot_list、hashtags。title不超过30个汉字；article为800至1500字原创分析，文末列出来源URL；video_script为60至120秒口播稿；shot_list为6至12条可由自有素材、授权素材、数据图表或AI生成画面实现的分镜，不要求搬用来源视频；hashtags为字符串数组。涉及AI生成内容时保留明确标识建议。"));
            JSONObject input = new JSONObject()
                    .put("topic", topic.trim())
                    .put("tags", tags == null ? "" : tags.trim())
                    .put("generated_at_ms", System.currentTimeMillis())
                    .put("sources", sourceValues);
            messages.put(new JSONObject().put("role", "user")
                    .put("content", input.toString()));
            request.put("messages", messages);
        } catch (JSONException impossible) {
            throw new IOException("无法构造原创内容请求", impossible);
        }

        String body;
        try {
            body = post(apiKey, request);
        } catch (ModelHttpException error) {
            // Some OpenAI-compatible providers do not implement response_format yet.
            if (error.statusCode != 400 || !request.has("response_format")) throw error;
            request.remove("response_format");
            body = post(apiKey, request);
        }
        try {
            String content = stripJsonFence(extractContent(body));
            return OriginalContentPackage.fromModelJson(
                    topic, new JSONObject(content), sources);
        } catch (JSONException error) {
            throw new IOException("模型没有返回有效的原创内容 JSON：" + error.getMessage(), error);
        }
    }

    private String post(String apiKey, JSONObject request) throws IOException {
        URL url = validatedEndpoint();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(35_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream bodyStream = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = bodyStream == null ? "" : readUtf8(bodyStream);
        connection.disconnect();
        if (code == 429) throw new IOException("模型调用达到限额，请稍后重试");
        if (code < 200 || code >= 300) {
            throw new ModelHttpException(code,
                    "模型请求失败：HTTP " + code + parseErrorMessage(body));
        }

        return body;
    }

    private URL validatedEndpoint() throws IOException {
        URL url = new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null
                || url.getHost().trim().isEmpty()) {
            throw new IOException("模型接口必须是有效的 HTTPS 地址");
        }
        return url;
    }

    private String extractContent(String body) throws JSONException, IOException {
        JSONObject response = new JSONObject(body);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IOException("模型没有返回内容");
        String content = choices.getJSONObject(0).getJSONObject("message")
                .optString("content", "").trim();
        if (content.isEmpty()) throw new IOException("模型返回了空内容");
        return content;
    }

    private String stripJsonFence(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                value = value.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }

    private String readUtf8(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("模型响应超过安全上限");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String parseErrorMessage(String body) {
        try {
            JSONObject value = new JSONObject(body);
            String message = value.optString("message", "");
            if (message.isEmpty()) {
                JSONObject error = value.optJSONObject("error");
                if (error != null) message = error.optString("message", "");
            }
            return message.isEmpty() ? "" : "（" + message + "）";
        } catch (JSONException ignored) {
            return "";
        }
    }

    private String cleanTitle(String content) {
        String title = content.trim().replace("\r", "").split("\n", 2)[0].trim();
        if ((title.startsWith("\"") && title.endsWith("\""))
                || (title.startsWith("“") && title.endsWith("”"))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        return title.length() > 30 ? title.substring(0, 30) : title;
    }

    private static final class ModelHttpException extends IOException {
        final int statusCode;

        ModelHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
