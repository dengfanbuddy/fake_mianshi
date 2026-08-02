import io

p = 'src/main/java/com/fakemianshi/service/impl/LlmServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

# 1. 追加"直接输出"系统指令常量 + buildMessages 方法
src = src.replace('''    private final AIModelConfigRepository configRepository;
    private final HttpClient httpClient;''',
'''    private final AIModelConfigRepository configRepository;
    private final HttpClient httpClient;

    /** 统一追加的系统指令：要求模型直接输出结果，减少/禁用思考过程（配合请求参数 thinking:disabled 双保险） */
    private static final String DIRECT_OUTPUT_HINT =
            "（系统要求：请直接输出最终结果，不要输出任何思考过程、推理过程或额外说明。）";

    /** 构造 system+user 消息，并在 system 末尾追加"直接输出"指令 */
    private List<Message> buildMessages(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt + DIRECT_OUTPUT_HINT));
        } else {
            messages.add(new Message("system", DIRECT_OUTPUT_HINT.trim()));
        }
        messages.add(new Message("user", userPrompt));
        return messages;
    }''')

# 2. chat(systemPrompt, userPrompt) 用 buildMessages
src = src.replace('''    public LlmResponse chat(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));
        return chat(messages);
    }''',
'''    public LlmResponse chat(String systemPrompt, String userPrompt) {
        return chat(buildMessages(systemPrompt, userPrompt));
    }''')

# 3. chat(systemPrompt, userPrompt, maxTokens) 用 buildMessages
src = src.replace('''    public LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));
        return chat(messages, maxTokens);
    }''',
'''    public LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chat(buildMessages(systemPrompt, userPrompt), maxTokens);
    }''')

# 4. chatStream 用 buildMessages
src = src.replace('''        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));

        String requestJson;
        try {
            requestJson = buildRequestBody(config, messages, maxTokens, true);''',
'''        List<Message> messages = buildMessages(systemPrompt, userPrompt);

        String requestJson;
        try {
            requestJson = buildRequestBody(config, messages, maxTokens, true);''')

# 5. buildRequestBody 加 thinking disabled
src = src.replace('''        root.put("temperature", 0.7);
        root.put("max_tokens", maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);
        root.put("stream", stream);
        return OBJECT_MAPPER.writeValueAsString(root);''',
'''        root.put("temperature", 0.7);
        root.put("max_tokens", maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);
        root.put("stream", stream);
        // 思考型模型（如 deepseek-v4-flash）禁用思考，直接输出内容，显著提速
        root.putObject("thinking").put("type", "disabled");
        return OBJECT_MAPPER.writeValueAsString(root);''')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('patched')
