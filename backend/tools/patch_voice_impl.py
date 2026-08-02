import io

p = 'src/main/java/com/fakemianshi/service/impl/VoiceServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

# 1. 加 voiceConfigService 可选注入 + resolveVoiceConfig
old1 = '''    private final TencentCloudProperties properties;
    private final HttpClient httpClient;

    /**
     * 生产环境构造器：创建带超时配置的默认 HttpClient。
     */
    @Autowired
    public VoiceServiceImpl(TencentCloudProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /**
     * 测试用构造器：可注入 mock 的 HttpClient，避免真实网络请求。
     */
    public VoiceServiceImpl(TencentCloudProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }'''
new1 = '''    private final TencentCloudProperties properties;
    private final HttpClient httpClient;

    /** 数据库语音配置（页面可配）；测试构造器不注入时回退环境变量 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fakemianshi.service.VoiceConfigService voiceConfigService;

    /**
     * 生产环境构造器：创建带超时配置的默认 HttpClient。
     */
    @Autowired
    public VoiceServiceImpl(TencentCloudProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /**
     * 测试用构造器：可注入 mock 的 HttpClient，避免真实网络请求。
     */
    public VoiceServiceImpl(TencentCloudProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** 解析当前生效的语音配置：数据库配置优先，回退环境变量 */
    private com.fakemianshi.entity.VoiceConfig resolveVoiceConfig() {
        if (voiceConfigService != null) {
            com.fakemianshi.entity.VoiceConfig cfg = voiceConfigService.resolveActive();
            if (cfg != null && cfg.isConfigured()) {
                return cfg;
            }
        }
        return null;
    }'''
if old1 in src:
    src = src.replace(old1, new1, 1)
    print('injection patched')
else:
    print('[WARN] injection anchor not found')

# 2. validateConfigured
old2 = '''        if (!properties.isConfigured()) {'''
if old2 in src:
    src = src.replace(old2, '''        if (resolveVoiceConfig() == null && !properties.isConfigured()) {''', 1)
    print('validate patched')
else:
    print('[WARN] validate anchor not found')

# 3. ASR URL
old3 = 'String responseBody = doPost(properties.getAsrUrl(), ASR_SERVICE, "SentenceRecognition", ASR_VERSION, payload);'
if old3 in src:
    src = src.replace(old3, 'com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();\n            String asrUrl = cfg != null ? cfg.getAsrUrl() : properties.getAsrUrl();\n            String responseBody = doPost(asrUrl, ASR_SERVICE, "SentenceRecognition", ASR_VERSION, payload);', 1)
    print('asr patched')
else:
    print('[WARN] asr anchor not found')

# 4. TTS URL
old4 = 'String responseBody = doPost(properties.getTtsUrl(), TTS_SERVICE, "TextToVoice", TTS_VERSION, payload);'
if old4 in src:
    src = src.replace(old4, 'com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();\n            String ttsUrl = cfg != null ? cfg.getTtsUrl() : properties.getTtsUrl();\n            String responseBody = doPost(ttsUrl, TTS_SERVICE, "TextToVoice", TTS_VERSION, payload);', 1)
    print('tts patched')
else:
    print('[WARN] tts anchor not found')

# 5. 签名
old5 = '''String authorization = tc3Sign(properties.getSecretId(), properties.getSecretKey(),
                service, host, action, version, timestamp, payload, properties.getRegion());'''
if old5 in src:
    src = src.replace(old5, '''com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();
        String secretId = cfg != null ? cfg.getSecretId() : properties.getSecretId();
        String secretKey = cfg != null ? cfg.getSecretKey() : properties.getSecretKey();
        String region = cfg != null ? cfg.getRegion() : properties.getRegion();
        String authorization = tc3Sign(secretId, secretKey,
                service, host, action, version, timestamp, payload, region);''', 1)
    print('sign patched')
else:
    print('[WARN] sign anchor not found')

# 6. X-TC-Region header
old6 = '.header("X-TC-Region", properties.getRegion())'
if old6 in src:
    src = src.replace(old6, '.header("X-TC-Region", cfg != null ? cfg.getRegion() : properties.getRegion())', 1)
    print('region header patched')
else:
    print('[WARN] region header anchor not found')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
