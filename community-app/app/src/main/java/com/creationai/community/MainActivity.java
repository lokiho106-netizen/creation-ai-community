package com.creationai.community;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.creationai.community.ai.SiliconFlowClient;
import com.creationai.community.ai.ContentPackageStore;
import com.creationai.community.ai.OriginalContentPackage;
import com.creationai.community.automation.CreationAccessibilityService;
import com.creationai.community.automation.WorkflowStatusStore;
import com.creationai.community.download.VideoLinkExtractor;
import com.creationai.community.download.MediaStoreVideoDownloader;
import com.creationai.community.download.ResolvedVideo;
import com.creationai.community.download.ToutiaoVideoResolver;
import com.creationai.community.edit.LocalVideoReeditor;
import com.creationai.community.news.NewsFeedClient;
import com.creationai.community.news.NewsItem;
import com.creationai.community.preflight.PreflightChecker;
import com.creationai.community.preflight.PreflightReport;
import com.creationai.community.settings.AppSettings;
import com.creationai.community.settings.ContentMode;
import com.creationai.community.settings.SettingsRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("UnsafeOptInUsageError") // Media3 Transformer is intentionally isolated here.
public final class MainActivity extends Activity {
    public static final String EXTRA_CAPTURE_CLIPBOARD = "capture_clipboard";
    public static final String EXTRA_ORIGINAL_TITLE = "original_title";
    private static final String PREF_RUNTIME = "runtime_state";
    private static final String PREF_LAST_URI = "last_downloaded_uri";
    private static final String PREF_LAST_TITLE = "last_downloaded_title";

    private final List<EditText> numericFields = new ArrayList<>();
    private SettingsRepository repository;
    private AppSettings model;
    private TextView preflightText;
    private TextView workflowStatus;
    private Spinner contentModeSpinner;
    private EditText keywords;
    private Spinner modeSpinner;
    private EditText sections;
    private EditText requiredTitleKeywords;
    private EditText excluded;
    private EditText minViews;
    private EditText minLikes;
    private EditText minFollowers;
    private EditText maxFollowers;
    private EditText maxAge;
    private EditText maxPages;
    private EditText targetCount;
    private EditText apiKey;
    private EditText modelEndpoint;
    private EditText aiModel;
    private EditText titleDraft;
    private EditText trimStartFrames;
    private EditText trimEndFrames;
    private EditText originalTopic;
    private EditText originalTags;
    private EditText newsFeedUrls;
    private EditText newsLookbackHours;
    private EditText newsMaxItems;
    private TextView originalStatus;
    private EditText generatedArticle;
    private EditText generatedVideoScript;
    private Button generateOriginalButton;
    private EditText sharedLink;
    private CheckBox requireReview;
    private CheckBox rightsConfirmed;
    private TextView linkStatus;
    private Button resolveButton;
    private Button reeditButton;
    private Button uploadButton;
    private Uri lastDownloadedUri;
    private LocalVideoReeditor videoReeditor;
    private LinearLayout authorizedFilterCard;
    private LinearLayout originalContentCard;
    private LinearLayout authorizedResolverCard;
    private Button authorizedStartButton;
    private Button authorizedStopButton;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new SettingsRepository(this);
        model = repository.load();
        lastDownloadedUri = restoreLastDownloadedUri();
        videoReeditor = new LocalVideoReeditor(this);
        setContentView(buildContent());
        processIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPreflight();
    }

    @Override
    protected void onDestroy() {
        videoReeditor.release();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column(16);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(root);

        TextView title = text("Pocket Studio", 26, Color.rgb(23, 32, 51));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView subtitle = text("无卡密 · 密钥只保存在本机 · 发布前人工确认", 14, Color.rgb(95, 107, 122));
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        LinearLayout preflight = card();
        preflight.addView(sectionTitle("1 运行环境"));
        preflightText = text("正在检查…", 14, Color.DKGRAY);
        preflightText.setPadding(0, dp(8), 0, dp(8));
        preflight.addView(preflightText);
        workflowStatus = text("流程：未运行", 13, Color.rgb(95, 107, 122));
        workflowStatus.setPadding(0, 0, 0, dp(8));
        preflight.addView(workflowStatus);
        Button accessibility = button("开启无障碍服务");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        preflight.addView(accessibility);
        root.addView(preflight);

        LinearLayout productMode = card();
        productMode.addView(sectionTitle("2 创作模式"));
        productMode.addView(text("优先开发资讯原创剪辑；授权素材快速处理保留为辅助模式。", 13,
                Color.rgb(95, 107, 122)));
        contentModeSpinner = new Spinner(this);
        contentModeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{ContentMode.ORIGINAL_NEWS.displayName,
                        ContentMode.AUTHORIZED_REUSE.displayName}));
        contentModeSpinner.setSelection(model.contentMode == ContentMode.ORIGINAL_NEWS ? 0 : 1);
        productMode.addView(contentModeSpinner);
        root.addView(productMode);

        authorizedFilterCard = card();
        authorizedFilterCard.addView(sectionTitle("3 授权素材筛选"));
        authorizedFilterCard.addView(label("运行模式"));
        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"频道巡检", "关键词搜索"}));
        modeSpinner.setSelection("搜索".equals(model.mode) ? 1 : 0);
        authorizedFilterCard.addView(modeSpinner);
        authorizedFilterCard.addView(label("巡检频道（多个用逗号分隔）"));
        sections = edit(model.section, false);
        authorizedFilterCard.addView(sections);
        authorizedFilterCard.addView(label("关键词（多个用逗号分隔）"));
        keywords = edit(model.keywords, false);
        authorizedFilterCard.addView(keywords);
        authorizedFilterCard.addView(label("标题必须包含任一标签/关键词"));
        requiredTitleKeywords = edit(model.requiredTitleKeywords, false);
        requiredTitleKeywords.setHint("例如：AI,芯片,大模型");
        authorizedFilterCard.addView(requiredTitleKeywords);
        authorizedFilterCard.addView(label("排除词"));
        excluded = edit(model.excludedKeywords, false);
        authorizedFilterCard.addView(excluded);
        minViews = numeric(authorizedFilterCard, "最低播放量（读不到时跳过候选）", model.minViews);
        minLikes = numeric(authorizedFilterCard, "最低点赞数", model.minLikes);
        minFollowers = numeric(authorizedFilterCard, "作者最低粉丝数（暂不参与自动筛选）", model.minFollowers);
        minFollowers.setEnabled(false);
        maxFollowers = numeric(authorizedFilterCard, "作者最高粉丝数（暂不参与自动筛选）", model.maxFollowers);
        maxFollowers.setEnabled(false);
        maxAge = numeric(authorizedFilterCard, "最长发布时间（小时）", model.maxAgeHours);
        maxPages = numeric(authorizedFilterCard, "最多翻页", model.maxPages);
        targetCount = numeric(authorizedFilterCard, "每轮目标视频数", model.targetCount);
        root.addView(authorizedFilterCard);

        LinearLayout ai = card();
        ai.addView(sectionTitle("4 模型与媒体处理"));
        ai.addView(text("支持 SiliconFlow、DeepSeek 等 OpenAI 兼容接口；密钥只加密保存在本机。", 13, Color.rgb(95, 107, 122)));
        ai.addView(label("API 完整地址"));
        modelEndpoint = edit(model.modelEndpoint, false);
        modelEndpoint.setHint("https://.../v1/chat/completions");
        ai.addView(modelEndpoint);
        ai.addView(label("模型 API Key"));
        apiKey = edit(model.siliconFlowKey, false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ai.addView(apiKey);
        ai.addView(label("模型名称（默认免费模型）"));
        aiModel = edit(model.siliconFlowModel, false);
        ai.addView(aiModel);
        ai.addView(label("原始标题 / 待改写标题"));
        titleDraft = edit(restoreLastDownloadedTitle(), false);
        titleDraft.setHint("解析视频后会自动填入，也可手工输入");
        ai.addView(titleDraft);
        Button rewriteTitle = button("用模型生成新标题");
        rewriteTitle.setOnClickListener(v -> rewriteTitle(rewriteTitle));
        ai.addView(rewriteTitle);
        trimStartFrames = numeric(ai, "裁掉片头（帧）", model.trimStartFrames);
        trimEndFrames = numeric(ai, "裁掉片尾（帧）", model.trimEndFrames);
        requireReview = new CheckBox(this);
        requireReview.setText("发布前必须停下，等待人工检查");
        requireReview.setChecked(model.requireReview);
        requireReview.setEnabled(false);
        ai.addView(requireReview);
        rightsConfirmed = new CheckBox(this);
        rightsConfirmed.setText("我确认只处理自己拥有权利或已获授权的视频");
        rightsConfirmed.setChecked(model.rightsConfirmed);
        ai.addView(rightsConfirmed);
        root.addView(ai);

        originalContentCard = card();
        originalContentCard.addView(sectionTitle("5 资讯原创剪辑"));
        originalContentCard.addView(text("从用户配置的 HTTPS RSS/Atom 信源读取最新资讯，按主题相关度排序，再生成带来源的图文稿、口播稿、封面提示词和分镜。", 13,
                Color.rgb(95, 107, 122)));
        originalContentCard.addView(label("原创主题"));
        originalTopic = edit(model.originalTopic, false);
        originalContentCard.addView(originalTopic);
        originalContentCard.addView(label("检索标签（逗号分隔）"));
        originalTags = edit(model.originalTags, false);
        originalContentCard.addView(originalTags);
        originalContentCard.addView(label("RSS/Atom 地址（每行一个，最多10个）"));
        newsFeedUrls = multilineEdit(model.newsFeedUrls, 6);
        newsFeedUrls.setHint("https://example.com/feed.xml\nhttps://example.org/rss");
        originalContentCard.addView(newsFeedUrls);
        newsLookbackHours = numeric(originalContentCard, "资讯回看时长（小时）", model.newsLookbackHours);
        newsMaxItems = numeric(originalContentCard, "最多交给模型的来源条数（1–30）", model.newsMaxItems);
        generateOriginalButton = button("检索并生成原创内容包");
        generateOriginalButton.setOnClickListener(v -> generateOriginalPackage());
        originalContentCard.addView(generateOriginalButton);
        originalStatus = text("尚未生成", 13, Color.rgb(95, 107, 122));
        originalStatus.setPadding(0, dp(6), 0, dp(6));
        originalContentCard.addView(originalStatus);
        originalContentCard.addView(label("生成的图文稿"));
        generatedArticle = multilineEdit("", 10);
        generatedArticle.setHint("生成后可在此人工编辑");
        originalContentCard.addView(generatedArticle);
        originalContentCard.addView(label("生成的视频口播稿与分镜"));
        generatedVideoScript = multilineEdit("", 10);
        generatedVideoScript.setHint("生成后显示口播稿、分镜、封面提示词和标签");
        originalContentCard.addView(generatedVideoScript);
        Button copyArticle = button("复制图文稿");
        copyArticle.setOnClickListener(v -> copyToClipboard("图文稿",
                generatedArticle.getText().toString()));
        originalContentCard.addView(copyArticle);
        Button copyVideo = button("复制视频脚本");
        copyVideo.setOnClickListener(v -> copyToClipboard("视频脚本",
                generatedVideoScript.getText().toString()));
        originalContentCard.addView(copyVideo);
        root.addView(originalContentCard);

        authorizedResolverCard = card();
        authorizedResolverCard.addView(sectionTitle("6 授权视频链接入口"));
        authorizedResolverCard.addView(text("在今日头条点“分享”，选择本应用；系统会把分享文字中的链接交给解析模块。", 13, Color.rgb(95, 107, 122)));
        sharedLink = edit("", false);
        sharedLink.setHint("等待从今日头条分享链接…");
        authorizedResolverCard.addView(sharedLink);
        linkStatus = text("尚未解析", 13, Color.rgb(95, 107, 122));
        linkStatus.setPadding(0, dp(6), 0, dp(6));
        authorizedResolverCard.addView(linkStatus);
        resolveButton = button("解析并下载到相册");
        resolveButton.setOnClickListener(v -> resolveAndDownload());
        authorizedResolverCard.addView(resolveButton);
        reeditButton = button("本地裁剪并导出新视频");
        reeditButton.setEnabled(lastDownloadedUri != null);
        reeditButton.setOnClickListener(v -> reeditLastVideo());
        authorizedResolverCard.addView(reeditButton);
        uploadButton = button("进入头条发布（停在最终发布前）");
        uploadButton.setEnabled(lastDownloadedUri != null);
        uploadButton.setOnClickListener(v -> launchPublishReview());
        authorizedResolverCard.addView(uploadButton);
        root.addView(authorizedResolverCard);

        contentModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyContentModeVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                applyContentModeVisibility();
            }
        });
        applyContentModeVisibility();

        Button save = button("保存配置并检查");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        authorizedStartButton = button("开始授权素材筛选（测试）");
        authorizedStartButton.setOnClickListener(v -> startDryRun());
        root.addView(authorizedStartButton);
        authorizedStopButton = button("停止当前流程");
        authorizedStopButton.setOnClickListener(v -> {
            sendBroadcast(new Intent(CreationAccessibilityService.ACTION_STOP).setPackage(getPackageName()));
            toast("已发送停止指令");
            refreshPreflight();
        });
        root.addView(authorizedStopButton);

        applyContentModeVisibility();

        TextView version = text("社区版 0.3.0-dev · 资讯聚合、原创内容包、标题模型、本地裁剪", 12, Color.rgb(95, 107, 122));
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(18), 0, 0);
        root.addView(version);
        return scroll;
    }

    private void receiveSharedText(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared == null) return;
        String link = new VideoLinkExtractor().firstSupportedUrl(shared.toString());
        sharedLink.setText(link == null ? shared : link);
        Toast.makeText(this, link == null ? "未识别到受支持的头条链接" : "已接收头条链接", Toast.LENGTH_LONG).show();
    }

    private void processIncomingIntent(Intent intent) {
        receiveSharedText(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_CAPTURE_CLIPBOARD, false)) {
            String originalTitle = intent.getStringExtra(EXTRA_ORIGINAL_TITLE);
            if (originalTitle != null && !originalTitle.trim().isEmpty()) {
                titleDraft.setText(originalTitle.trim());
            }
            mainHandler.postDelayed(this::captureCopiedLink, 500);
        }
    }

    private void captureCopiedLink() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            linkStatus.setText("无法访问系统剪贴板，请手工粘贴分享文本");
            return;
        }
        final ClipData clip;
        try {
            clip = clipboard.getPrimaryClip();
        } catch (SecurityException denied) {
            linkStatus.setText("系统未允许读取剪贴板，请长按输入框手工粘贴");
            return;
        }
        if (clip == null || clip.getItemCount() == 0) {
            linkStatus.setText("没有读到刚复制的分享文本，请手工粘贴");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        String link = new VideoLinkExtractor().firstSupportedUrl(text == null ? "" : text.toString());
        if (link == null) {
            linkStatus.setText("剪贴板中没有受支持的今日头条链接");
            return;
        }
        sharedLink.setText(link);
        linkStatus.setText("已提取链接。请确认当前视频确有使用权，再手动点击解析下载");
    }

    private void resolveAndDownload() {
        String value = new VideoLinkExtractor().firstSupportedUrl(sharedLink.getText().toString());
        if (value == null) {
            toast("未识别到今日头条/西瓜视频链接");
            return;
        } else if (!rightsConfirmed.isChecked()) {
            toast("请先确认视频使用权");
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            toast("请允许保存视频，然后再次点击下载");
            return;
        }

        sharedLink.setText(value);
        resolveButton.setEnabled(false);
        linkStatus.setText("正在读取今日头条页面…");
        ioExecutor.execute(() -> {
            try {
                ResolvedVideo video = new ToutiaoVideoResolver().resolve(value);
                mainHandler.post(() -> {
                    titleDraft.setText(video.title);
                    linkStatus.setText("已解析 " + video.height + "p，准备下载：" + video.title);
                });
                Uri saved = new MediaStoreVideoDownloader(this).download(video, (downloaded, total) -> {
                    long percent = total > 0 ? downloaded * 100 / total : -1;
                    mainHandler.post(() -> linkStatus.setText(percent >= 0
                            ? String.format(Locale.CHINA, "正在下载：%d%%", percent)
                            : String.format(Locale.CHINA, "正在下载：%.1f MB", downloaded / 1048576d)));
                });
                mainHandler.post(() -> {
                    linkStatus.setText("下载完成，已保存到 Movies/CreationAI\n" + saved);
                    lastDownloadedUri = saved;
                    rememberLastDownloadedMedia(saved, video.title);
                    resolveButton.setEnabled(true);
                    reeditButton.setEnabled(true);
                    uploadButton.setEnabled(true);
                    toast("视频已保存到相册");
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    linkStatus.setText("失败：" + safeMessage(error));
                    resolveButton.setEnabled(true);
                });
            }
        });
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void reeditLastVideo() {
        if (lastDownloadedUri == null) {
            toast("请先解析并下载一个视频");
            return;
        }
        saveSettings();
        reeditButton.setEnabled(false);
        resolveButton.setEnabled(false);
        linkStatus.setText("正在准备本地裁剪…");
        videoReeditor.startByFrames(
                lastDownloadedUri,
                model.trimStartFrames,
                model.trimEndFrames,
                new LocalVideoReeditor.Listener() {
                    @Override
                    public void onProgress(int percent) {
                        linkStatus.setText("本地导出：" + percent + "%");
                    }

                    @Override
                    public void onCompleted(Uri outputUri) {
                        lastDownloadedUri = outputUri;
                        rememberLastDownloadedMedia(outputUri, titleDraft.getText().toString().trim());
                        linkStatus.setText("本地裁剪完成：" + outputUri);
                        resolveButton.setEnabled(true);
                        reeditButton.setEnabled(true);
                        uploadButton.setEnabled(true);
                        toast("剪辑后视频已保存到相册");
                    }

                    @Override
                    public void onError(String message) {
                        linkStatus.setText(message);
                        resolveButton.setEnabled(true);
                        reeditButton.setEnabled(true);
                    }
                });
    }

    private void launchPublishReview() {
        if (lastDownloadedUri == null) {
            toast("请先下载一个视频");
            return;
        }
        String publishTitle = localTitleCleanup(titleDraft.getText().toString());
        if (publishTitle.isEmpty()) {
            toast("请先填写并确认标题");
            return;
        }
        saveSettings();
        PreflightReport report = new PreflightChecker(this).run();
        if (!report.canStart()) {
            toast("请先确认头条 15.4.0、无障碍和网络均可用");
            return;
        }
        Intent command = new Intent(CreationAccessibilityService.ACTION_UPLOAD_REVIEW)
                .setPackage(getPackageName())
                .putExtra(CreationAccessibilityService.EXTRA_UPLOAD_TITLE, publishTitle);
        sendBroadcast(command);
        Intent launch = getPackageManager().getLaunchIntentForPackage(PreflightChecker.TOUTIAO_PACKAGE);
        if (launch == null) {
            toast("无法启动今日头条");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launch);
    }

    private void rememberLastDownloadedMedia(Uri uri, String title) {
        getSharedPreferences(PREF_RUNTIME, MODE_PRIVATE).edit()
                .putString(PREF_LAST_URI, uri == null ? "" : uri.toString())
                .putString(PREF_LAST_TITLE, title == null ? "" : title.trim())
                .apply();
    }

    private String restoreLastDownloadedTitle() {
        return getSharedPreferences(PREF_RUNTIME, MODE_PRIVATE)
                .getString(PREF_LAST_TITLE, "");
    }

    private Uri restoreLastDownloadedUri() {
        String stored = getSharedPreferences(PREF_RUNTIME, MODE_PRIVATE)
                .getString(PREF_LAST_URI, "");
        if (stored != null && !stored.trim().isEmpty()) return Uri.parse(stored);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        String[] projection = {MediaStore.Video.Media._ID};
        String selection = MediaStore.Video.Media.RELATIVE_PATH + "=? AND "
                + MediaStore.Video.Media.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.Video.Media.IS_PENDING + "=0";
        String[] arguments = {"Movies/CreationAI/", "CreationAI_%"};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arguments,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id));
            }
        } catch (RuntimeException ignored) {
            // A missing media permission or vendor MediaStore issue simply disables the button.
        }
        return null;
    }

    private void rewriteTitle(Button button) {
        saveSettings();
        String original = titleDraft.getText().toString().trim();
        if (original.isEmpty()) {
            toast("请先填写原始标题或解析一个视频");
            return;
        }
        if (model.siliconFlowKey.isEmpty()) {
            titleDraft.setText(localTitleCleanup(original));
            toast("未填写密钥，已使用本地规则整理标题");
            return;
        }
        button.setEnabled(false);
        button.setText("模型生成中…");
        ioExecutor.execute(() -> {
            try {
                String rewritten = new SiliconFlowClient(model.modelEndpoint).rewriteTitle(
                        model.siliconFlowKey, model.siliconFlowModel, original);
                mainHandler.post(() -> {
                    titleDraft.setText(rewritten);
                    button.setText("用模型生成新标题");
                    button.setEnabled(true);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    toast("标题生成失败：" + safeMessage(error));
                    button.setText("用模型生成新标题");
                    button.setEnabled(true);
                });
            }
        });
    }

    private String localTitleCleanup(String value) {
        String cleaned = value.trim()
                .replaceAll("^[【\\[\\s]+|[】\\]\\s]+$", "")
                .replaceAll("[!！]{2,}", "！")
                .replaceAll("[?？]{2,}", "？")
                .replaceAll("\\s+", "");
        return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
    }

    private void generateOriginalPackage() {
        saveSettings(false);
        if (model.originalTopic.isEmpty()) {
            toast("请填写原创主题");
            return;
        }
        if (model.newsFeedUrls.isEmpty()) {
            toast("请至少填写一个 RSS/Atom 资讯源");
            return;
        }
        if (model.siliconFlowKey.isEmpty()) {
            toast("生成原创内容包需要模型 API Key");
            return;
        }
        generateOriginalButton.setEnabled(false);
        originalStatus.setText("正在读取最新资讯…");
        ioExecutor.execute(() -> {
            try {
                List<NewsItem> sources = new NewsFeedClient().fetch(
                        model.newsFeedUrls,
                        model.originalTopic,
                        model.originalTags,
                        model.newsLookbackHours,
                        model.newsMaxItems);
                mainHandler.post(() -> originalStatus.setText(
                        "已筛选 " + sources.size() + " 条来源，正在生成图文与视频脚本…"));
                OriginalContentPackage generated = new SiliconFlowClient(model.modelEndpoint)
                        .generateOriginalContent(
                                model.siliconFlowKey,
                                model.siliconFlowModel,
                                model.originalTopic,
                                model.originalTags,
                                sources);
                File saved = new ContentPackageStore(this).save(generated);
                mainHandler.post(() -> showGeneratedPackage(generated, saved));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    originalStatus.setText("生成失败：" + safeMessage(error));
                    generateOriginalButton.setEnabled(true);
                });
            }
        });
    }

    private void showGeneratedPackage(OriginalContentPackage generated, File saved) {
        titleDraft.setText(generated.title);
        generatedArticle.setText(generated.article);
        StringBuilder video = new StringBuilder(generated.videoScript);
        if (!generated.shotList.isEmpty()) {
            video.append("\n\n分镜：");
            for (int i = 0; i < generated.shotList.size(); i++) {
                video.append("\n").append(i + 1).append(". ").append(generated.shotList.get(i));
            }
        }
        if (!generated.coverPrompt.isEmpty()) {
            video.append("\n\n封面提示词：\n").append(generated.coverPrompt);
        }
        if (!generated.hashtags.isEmpty()) {
            video.append("\n\n标签：");
            for (String tag : generated.hashtags) video.append(" #").append(tag.replace("#", ""));
        }
        generatedVideoScript.setText(video.toString());
        originalStatus.setText("生成完成，已保存内容包：\n" + saved.getAbsolutePath()
                + "\n来源 " + generated.sources.size() + " 条；发布前请逐条核验事实和来源。");
        generateOriginalButton.setEnabled(true);
        toast("原创图文稿和视频脚本已生成");
    }

    private void applyContentModeVisibility() {
        boolean originalMode = contentModeSpinner.getSelectedItemPosition() == 0;
        if (authorizedFilterCard != null) {
            authorizedFilterCard.setVisibility(originalMode ? View.GONE : View.VISIBLE);
        }
        if (originalContentCard != null) {
            originalContentCard.setVisibility(originalMode ? View.VISIBLE : View.GONE);
        }
        if (authorizedResolverCard != null) {
            authorizedResolverCard.setVisibility(originalMode ? View.GONE : View.VISIBLE);
        }
        if (authorizedStartButton != null) {
            authorizedStartButton.setVisibility(originalMode ? View.GONE : View.VISIBLE);
        }
        if (authorizedStopButton != null) {
            authorizedStopButton.setVisibility(originalMode ? View.GONE : View.VISIBLE);
        }
    }

    private void copyToClipboard(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            toast("没有可复制的内容");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("无法访问剪贴板");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        toast(label + "已复制");
    }

    private void saveSettings() {
        saveSettings(true);
    }

    private void saveSettings(boolean showToast) {
        model.contentMode = contentModeSpinner.getSelectedItemPosition() == 0
                ? ContentMode.ORIGINAL_NEWS : ContentMode.AUTHORIZED_REUSE;
        model.mode = modeSpinner.getSelectedItemPosition() == 1 ? "搜索" : "频道";
        model.section = sections.getText().toString().trim();
        model.keywords = keywords.getText().toString().trim();
        model.requiredTitleKeywords = requiredTitleKeywords.getText().toString().trim();
        model.excludedKeywords = excluded.getText().toString().trim();
        model.minViews = number(minViews, 1000);
        model.minLikes = number(minLikes, 0);
        model.minFollowers = number(minFollowers, 0);
        model.maxFollowers = number(maxFollowers, 0);
        model.maxAgeHours = number(maxAge, 72);
        model.maxPages = number(maxPages, 20);
        model.targetCount = number(targetCount, 1);
        model.siliconFlowKey = apiKey.getText().toString().trim();
        model.modelEndpoint = modelEndpoint.getText().toString().trim();
        model.siliconFlowModel = aiModel.getText().toString().trim();
        model.trimStartFrames = number(trimStartFrames, 2);
        model.trimEndFrames = number(trimEndFrames, 2);
        model.originalTopic = originalTopic.getText().toString().trim();
        model.originalTags = originalTags.getText().toString().trim();
        model.newsFeedUrls = newsFeedUrls.getText().toString().trim();
        model.newsLookbackHours = number(newsLookbackHours, 48);
        model.newsMaxItems = Math.min(30, Math.max(1, number(newsMaxItems, 12)));
        model.requireReview = true;
        model.rightsConfirmed = rightsConfirmed.isChecked();
        repository.save(model);
        refreshPreflight();
        if (showToast) toast("配置已保存在本机");
    }

    private void startDryRun() {
        saveSettings();
        PreflightReport report = new PreflightChecker(this).run();
        if (!report.accessibilityEnabled) {
            toast("请先开启无障碍服务");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (!report.toutiaoInstalled) {
            toast("未检测到固定版今日头条");
            return;
        }
        if (!report.toutiaoVersionCompatible) {
            toast("今日头条版本不匹配：需要 15.4.0，当前 " + report.toutiaoVersion);
            return;
        }
        sendBroadcast(new Intent(CreationAccessibilityService.ACTION_START).setPackage(getPackageName()));
        Intent launch = getPackageManager().getLaunchIntentForPackage(PreflightChecker.TOUTIAO_PACKAGE);
        if (launch != null) startActivity(launch);
    }

    private void refreshPreflight() {
        if (preflightText == null) return;
        PreflightReport report = new PreflightChecker(this).run();
        String status = "今日头条：" + (report.toutiaoInstalled
                ? "已安装（" + report.toutiaoVersion + "，要求 15.4.0）" : "未安装")
                + "\n无障碍：" + (report.accessibilityEnabled ? "已开启" : "未开启")
                + "\n网络：" + (report.networkAvailable ? "可用" : "不可用");
        preflightText.setText(status);
        preflightText.setTextColor(report.canStart() ? Color.rgb(19, 115, 51) : Color.rgb(176, 96, 0));
        if (workflowStatus != null) {
            workflowStatus.setText("流程：" + new WorkflowStatusStore(this).readDisplayText());
        }
    }

    private LinearLayout card() {
        LinearLayout card = column(8);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(226, 231, 239));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout column(int spacing) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        layout.setDividerPadding(dp(spacing));
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Color.rgb(23, 32, 51));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, Color.rgb(95, 107, 122));
        view.setPadding(0, dp(8), 0, dp(3));
        return view;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private EditText edit(String value, boolean numeric) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setTextSize(15);
        field.setSingleLine(true);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        return field;
    }

    private EditText multilineEdit(String value, int minLines) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setTextSize(14);
        field.setSingleLine(false);
        field.setMinLines(minLines);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return field;
    }

    private EditText numeric(LinearLayout parent, String title, int value) {
        parent.addView(label(title));
        EditText field = edit(String.valueOf(value), true);
        parent.addView(field);
        numericFields.add(field);
        return field;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        return button;
    }

    private int number(EditText field, int fallback) {
        try {
            int parsed = Integer.parseInt(field.getText().toString().trim());
            return Math.max(0, parsed);
        } catch (NumberFormatException ignored) {
            field.setText(String.valueOf(fallback));
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
