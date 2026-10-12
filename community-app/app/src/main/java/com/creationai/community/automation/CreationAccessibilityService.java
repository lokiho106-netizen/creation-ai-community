package com.creationai.community.automation;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.creationai.community.MainActivity;
import com.creationai.community.preflight.PreflightChecker;
import com.creationai.community.settings.AppSettings;
import com.creationai.community.settings.SettingsRepository;

import java.util.HashSet;
import java.util.Set;

public final class CreationAccessibilityService extends AccessibilityService {
    public static final String ACTION_START = "com.creationai.community.action.START";
    public static final String ACTION_STOP = "com.creationai.community.action.STOP";
    public static final String ACTION_UPLOAD_REVIEW =
            "com.creationai.community.action.UPLOAD_REVIEW";
    public static final String EXTRA_UPLOAD_TITLE = "upload_title";

    private final WorkflowController workflow = new WorkflowController();
    private final ToutiaoAdapter adapter = new ToutiaoAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WorkflowStatusStore statusStore;
    private AppSettings settings;
    private String lastEventClass = "";
    private String currentTitle = "";
    private String uploadTitle = "";
    private int pageAttempts;
    private int channelScrollAttempts;
    private int channelClickAttempts;
    private int shareScrollAttempts;
    private final Set<String> visitedTitles = new HashSet<>();
    private boolean receiverRegistered;

    private final BroadcastReceiver controls = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_START.equals(intent.getAction())) {
                settings = new SettingsRepository(CreationAccessibilityService.this).load();
                pageAttempts = 0;
                channelScrollAttempts = 0;
                channelClickAttempts = 0;
                shareScrollAttempts = 0;
                currentTitle = "";
                visitedTitles.clear();
                move(WorkflowState.OPEN_TOUTIAO, "等待固定版今日头条 15.4.0 首页");
                scheduleProcess(200);
            } else if (ACTION_UPLOAD_REVIEW.equals(intent.getAction())) {
                settings = new SettingsRepository(CreationAccessibilityService.this).load();
                uploadTitle = intent.getStringExtra(EXTRA_UPLOAD_TITLE);
                if (uploadTitle == null) uploadTitle = "";
                move(WorkflowState.PREPARE_PUBLISH, "等待头条首页，准备进入视频发布");
                scheduleProcess(200);
            } else if (ACTION_STOP.equals(intent.getAction())) {
                workflow.stop("用户停止任务");
                writeStatus();
            }
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!PreflightChecker.TOUTIAO_PACKAGE.contentEquals(event.getPackageName())) return;
        lastEventClass = event.getClassName() == null ? "" : event.getClassName().toString();
        scheduleProcess(300);
    }

    @Override
    public void onInterrupt() {
        workflow.stop("无障碍服务被系统中断");
        writeStatus();
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 has no not-exported flag overload.
    protected void onServiceConnected() {
        super.onServiceConnected();
        statusStore = new WorkflowStatusStore(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_UPLOAD_REVIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controls, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controls, filter);
        }
        receiverRegistered = true;
        move(WorkflowState.IDLE, "服务已连接，等待用户开始");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(controls);
        receiverRegistered = false;
        super.onDestroy();
    }

    private void scheduleProcess(long delayMs) {
        handler.removeCallbacks(processWindow);
        handler.postDelayed(processWindow, delayMs);
    }

    private final Runnable processWindow = new Runnable() {
        @Override
        public void run() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || settings == null) return;
            if (adapter.dismissKnownBlockingOverlay(root)) {
                update("已关闭头条账号授权提示");
                scheduleProcess(700);
                return;
            }
            switch (workflow.getState()) {
                case OPEN_TOUTIAO:
                    handleHome(root);
                    break;
                case SELECT_CHANNEL:
                    handleChannel(root);
                    break;
                case ENTER_KEYWORD:
                    handleSearchEntry(root);
                    break;
                case SCAN_RESULTS:
                    handleCandidates(root);
                    break;
                case INSPECT_CANDIDATE:
                    handleCandidateDetail(root);
                    break;
                case SHARE_LINK:
                    handleSharePanel(root);
                    break;
                case PREPARE_PUBLISH:
                    handlePreparePublish(root);
                    break;
                case PICK_PUBLISH_VIDEO:
                    handlePickPublishVideo(root);
                    break;
                case PREVIEW_PUBLISH_VIDEO:
                    handlePublishPreview(root);
                    break;
                case WAIT_FOR_REVIEW:
                    handleFinalPublishReview(root);
                    break;
                default:
                    break;
            }
        }
    };

    private void handleHome(AccessibilityNodeInfo root) {
        if (!adapter.isHome(root)) {
            update("尚未识别到今日头条首页");
            return;
        }
        if ("搜索".equals(settings.mode)) {
            if (firstToken(settings.keywords).isEmpty()) {
                move(WorkflowState.ERROR, "搜索模式没有填写关键词");
            } else if (adapter.openSearch(root)) {
                move(WorkflowState.ENTER_KEYWORD, "已打开搜索页");
            } else {
                move(WorkflowState.ERROR, "无法点击搜索入口");
            }
        } else {
            move(WorkflowState.SELECT_CHANNEL, "准备选择频道：" + firstToken(settings.section));
            scheduleProcess(100);
        }
    }

    private void handleChannel(AccessibilityNodeInfo root) {
        String channel = firstToken(settings.section);
        if (channel.isEmpty()) channel = "推荐";
        if (adapter.isChannelSelected(root, channel)) {
            pageAttempts = 0;
            move(WorkflowState.SCAN_RESULTS, "已确认频道“" + channel + "”处于选中状态，开始读取卡片");
            scheduleProcess(900);
        } else if (adapter.isChannelVisible(root, channel)) {
            if (channelClickAttempts++ < 3 && adapter.selectChannel(root, channel)) {
                update("已点击频道“" + channel + "”，等待选中状态生效");
                scheduleProcess(800);
            } else {
                move(WorkflowState.ERROR, "频道“" + channel + "”可见，但未能切换为选中状态");
            }
        } else if (channelScrollAttempts++ < 8 && adapter.scrollChannelsForward(root)) {
            update("横向查找频道“" + channel + "”");
            scheduleProcess(500);
        } else {
            move(WorkflowState.ERROR, "找不到频道“" + channel + "”");
        }
    }

    private void handleSearchEntry(AccessibilityNodeInfo root) {
        String keyword = firstToken(settings.keywords);
        if (adapter.enterKeywordAndSearch(root, keyword)) {
            pageAttempts = 0;
            move(WorkflowState.SCAN_RESULTS, "已搜索“" + keyword + "”，读取结果");
            scheduleProcess(1_000);
        } else if (workflow.timedOut(8_000)) {
            move(WorkflowState.ERROR, "搜索框或搜索按钮超时");
        } else {
            update("等待搜索输入框");
        }
    }

    private void handleCandidates(AccessibilityNodeInfo root) {
        ToutiaoAdapter.Candidate candidate = adapter.firstMatchingCandidate(root, settings, visitedTitles);
        if (candidate != null) {
            currentTitle = candidate.title;
            visitedTitles.add(candidate.title);
            if (adapter.clickCandidate(candidate)) {
                String viewSummary = candidate.views >= 0
                        ? candidate.views + "播放" : "播放量不可见";
                move(WorkflowState.INSPECT_CANDIDATE,
                        "候选：" + candidate.ageHours + "小时内，" + viewSummary
                                + "，" + candidate.likes + "赞");
            } else {
                update("候选卡片点击失败，准备继续翻页");
                scheduleProcess(600);
            }
            return;
        }

        if (pageAttempts >= Math.max(1, settings.maxPages)) {
            move(WorkflowState.PAUSED, "达到最多翻页次数，没有找到符合项");
        } else if (adapter.scrollFeedForward(root)) {
            pageAttempts++;
            update("本页无符合项，翻页 " + pageAttempts + "/" + settings.maxPages);
            scheduleProcess(900);
        } else if (workflow.timedOut(8_000)) {
            move(WorkflowState.ERROR, "列表无法继续滚动");
        }
    }

    private void handleCandidateDetail(AccessibilityNodeInfo root) {
        if (!adapter.isVideoDetail(lastEventClass, root)) {
            if (workflow.timedOut(6_000)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                move(WorkflowState.SCAN_RESULTS, "候选不是视频详情，已返回");
                scheduleProcess(800);
            } else {
                update("正在确认候选是否为视频");
            }
            return;
        }
        if (!settings.rightsConfirmed) {
            move(WorkflowState.PAUSED, "未确认素材使用权，停止获取链接");
        } else if (adapter.openSharePanel(root)) {
            shareScrollAttempts = 0;
            move(WorkflowState.SHARE_LINK, "已打开分享面板，等待复制链接");
        } else if (workflow.timedOut(8_000)) {
            move(WorkflowState.ERROR, "视频详情页找不到分享按钮");
        }
    }

    private void handleSharePanel(AccessibilityNodeInfo root) {
        if (adapter.copyLink(root)) {
            move(WorkflowState.PAUSED, "已复制链接，交给社区版解析下载");
            handler.postDelayed(this::launchClipboardCapture, 700);
        } else if (shareScrollAttempts++ < 4 && adapter.scrollShareTargetsForward(root)) {
            update("横向查找“复制链接” " + shareScrollAttempts + "/4");
            scheduleProcess(500);
        } else if (workflow.timedOut(8_000)) {
            move(WorkflowState.ERROR, "分享面板找不到“复制链接”");
        } else {
            update("等待分享面板");
            scheduleProcess(400);
        }
    }

    private void handlePreparePublish(AccessibilityNodeInfo root) {
        if (adapter.isPublishChooser(lastEventClass, root)) {
            move(WorkflowState.PICK_PUBLISH_VIDEO, "已进入视频相册，准备选择最新视频");
            scheduleProcess(200);
        } else if (adapter.isHome(root) && adapter.openPublishEntry(root)) {
            move(WorkflowState.PICK_PUBLISH_VIDEO, "已点击头条发布入口，等待视频相册");
            scheduleProcess(700);
        } else if (workflow.timedOut(12_000)) {
            move(WorkflowState.ERROR, "无法从头条首页进入发布相册");
        } else {
            update("等待今日头条首页");
            scheduleProcess(500);
        }
    }

    private void handlePickPublishVideo(AccessibilityNodeInfo root) {
        if (adapter.isVideoPreview(lastEventClass, root)) {
            move(WorkflowState.PREVIEW_PUBLISH_VIDEO, "已进入视频预览，准备点击下一步");
            scheduleProcess(200);
        } else if (adapter.isPublishChooser(lastEventClass, root)
                && adapter.selectNewestVideo(root)) {
            move(WorkflowState.PREVIEW_PUBLISH_VIDEO, "已选择相册最新视频，等待预览页");
            scheduleProcess(800);
        } else if (workflow.timedOut(15_000)) {
            move(WorkflowState.ERROR, "视频相册未找到最新视频");
        } else {
            update("等待视频相册加载");
            scheduleProcess(500);
        }
    }

    private void handlePublishPreview(AccessibilityNodeInfo root) {
        if (adapter.isFinalPublishReview(lastEventClass, root)) {
            move(WorkflowState.WAIT_FOR_REVIEW, "已进入最终发布页，准备填写标题");
            scheduleProcess(200);
        } else if (adapter.isVideoPreview(lastEventClass, root)
                && adapter.continueFromVideoPreview(root)) {
            move(WorkflowState.WAIT_FOR_REVIEW, "已点击下一步，等待最终发布页");
            scheduleProcess(800);
        } else if (workflow.timedOut(20_000)) {
            move(WorkflowState.ERROR, "视频预览页未能进入下一步");
        } else {
            update("等待视频预览加载");
            scheduleProcess(500);
        }
    }

    private void handleFinalPublishReview(AccessibilityNodeInfo root) {
        if (adapter.isFinalPublishReview(lastEventClass, root)) {
            if (!uploadTitle.trim().isEmpty() && !adapter.fillFinalTitle(root, uploadTitle)) {
                move(WorkflowState.ERROR, "已到发布页，但标题填写失败");
                return;
            }
            move(WorkflowState.PAUSED,
                    "已到最终发布页并填写标题；请人工复核，程序不会点击发布");
        } else if (workflow.timedOut(20_000)) {
            move(WorkflowState.ERROR, "等待最终发布页超时");
        } else {
            update("等待最终发布页加载");
            scheduleProcess(500);
        }
    }

    private void launchClipboardCapture() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_CAPTURE_CLIPBOARD, true);
        intent.putExtra(MainActivity.EXTRA_ORIGINAL_TITLE, currentTitle);
        startActivity(intent);
    }

    private String firstToken(String value) {
        if (value == null) return "";
        String[] parts = value.split("[,，;；\\n]");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    private void move(WorkflowState state, String message) {
        workflow.moveTo(state, message);
        writeStatus();
    }

    private void update(String message) {
        statusStore.write(workflow.getState(), message);
    }

    private void writeStatus() {
        if (statusStore != null) statusStore.write(workflow.getState(), workflow.getMessage());
    }
}
