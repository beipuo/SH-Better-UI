package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.Objects;

/** 山海：可着色的 16×16 GUI 图标，可使用库内图标或调用方的透明 PNG。 */
public record GraphicIcon(ResourceLocation texture, GraphicIconShader shader, GraphicIconMotion motion) {
    public static final int PIXELS = 16;
    public static final GraphicIcon INFO = builtIn("info");
    public static final GraphicIcon SUCCESS = builtIn("success");
    public static final GraphicIcon WARNING = builtIn("warning");
    public static final GraphicIcon CRITICAL_WARNING = builtIn("critical_warning");
    public static final GraphicIcon ERROR = builtIn("error");
    public static final GraphicIcon STATUS_READY = builtIn("status_ready");
    public static final GraphicIcon STATUS_PENDING = builtIn("status_pending").withMotion(GraphicIconMotion.SPIN);
    public static final GraphicIcon STATUS_SUCCEEDED = builtIn("status_succeeded");
    public static final GraphicIcon STATUS_PARTIAL = builtIn("status_partial");
    public static final GraphicIcon STATUS_FAILED = builtIn("status_failed");
    public static final GraphicIcon STATUS_CANCELLED = builtIn("status_cancelled");
    public static final GraphicIcon STATUS_TIMED_OUT = builtIn("status_timed_out");
    public static final GraphicIcon PREVIOUS = builtIn("previous");
    public static final GraphicIcon NEXT = builtIn("next");
    public static final GraphicIcon BACK = builtIn("back");
    public static final GraphicIcon FORWARD = builtIn("forward");
    public static final GraphicIcon SWITCH = builtIn("switch");
    public static final GraphicIcon MERGE = builtIn("merge");
    public static final GraphicIcon SEPARATE = builtIn("separate");
    public static final GraphicIcon NEW = builtIn("new");
    public static final GraphicIcon SAVE = builtIn("save");
    public static final GraphicIcon EXPORT = builtIn("export");
    public static final GraphicIcon RUN = builtIn("run");
    public static final GraphicIcon PAUSE = builtIn("pause");
    public static final GraphicIcon RESUME = builtIn("resume");
    public static final GraphicIcon ADD = builtIn("add");
    public static final GraphicIcon REMOVE = builtIn("remove");
    public static final GraphicIcon SEARCH = builtIn("search");
    public static final GraphicIcon FILTER = builtIn("filter");
    public static final GraphicIcon SORT = builtIn("sort");
    public static final GraphicIcon EDIT = builtIn("edit");
    public static final GraphicIcon COPY = builtIn("copy");
    public static final GraphicIcon DELETE = builtIn("delete");
    public static final GraphicIcon FAVORITE = builtIn("favorite");
    public static final GraphicIcon LOCK = builtIn("lock");
    public static final GraphicIcon UNLOCK = builtIn("unlock");
    public static final GraphicIcon COMPRESS = builtIn("compress");
    public static final GraphicIcon DECOMPRESS = builtIn("decompress");
    public static final GraphicIcon ENCRYPT = builtIn("encrypt");
    public static final GraphicIcon DECRYPT = builtIn("decrypt");
    public static final GraphicIcon EXPAND = builtIn("expand");
    public static final GraphicIcon COLLAPSE = builtIn("collapse");
    public static final GraphicIcon UPLOAD = builtIn("upload");
    public static final GraphicIcon DOWNLOAD = builtIn("download");
    public static final GraphicIcon SETTINGS = builtIn("settings");
    public static final GraphicIcon REFRESH = builtIn("refresh");
    public static final GraphicIcon REPAIR = builtIn("repair");
    public static final GraphicIcon RESET = builtIn("reset");
    public static final GraphicIcon DISABLE = builtIn("disable");
    public static final GraphicIcon ENABLE = builtIn("enable");
    public static final GraphicIcon HIDE = builtIn("hide");
    public static final GraphicIcon SHOW = builtIn("show");
    public static final GraphicIcon ORGANIZE = builtIn("organize");
    public static final GraphicIcon CACHE = builtIn("cache");
    public static final GraphicIcon LOG = builtIn("log");
    public static final GraphicIcon MONITOR = builtIn("monitor");
    public static final GraphicIcon TODO = builtIn("todo");
    public static final GraphicIcon DOCUMENT = builtIn("document");
    public static final GraphicIcon CODE = builtIn("code");
    public static final GraphicIcon TEST = builtIn("test");
    public static final GraphicIcon DEVELOPMENT = builtIn("development");
    public static final GraphicIcon COLLECTION = builtIn("collection");
    public static final GraphicIcon HISTORY = builtIn("history");
    public static final GraphicIcon USER = builtIn("user");
    public static final GraphicIcon SYSTEM = builtIn("system");
    public static final GraphicIcon PHONE = builtIn("phone");
    public static final GraphicIcon COMPUTER = builtIn("computer");
    public static final GraphicIcon MINECRAFT = builtIn("minecraft");
    public static final GraphicIcon GAMEPAD = builtIn("gamepad");
    public static final GraphicIcon MOUSE = builtIn("mouse");
    public static final GraphicIcon KEYBOARD = builtIn("keyboard");
    public static final GraphicIcon HEADPHONES = builtIn("headphones");
    public static final GraphicIcon MICROPHONE = builtIn("microphone");
    public static final GraphicIcon FORGE = builtIn("forge");
    public static final GraphicIcon NEOFORGE = builtIn("neoforge");
    public static final GraphicIcon BLOCK = builtIn("block");
    public static final GraphicIcon ITEM = builtIn("item");
    public static final GraphicIcon ATTRIBUTES = builtIn("attributes");
    public static final GraphicIcon DESCRIPTION = builtIn("description");
    public static final GraphicIcon TOOLTIP = builtIn("tooltip");
    public static final GraphicIcon INTERACTION = builtIn("interaction");
    public static final GraphicIcon AE2 = builtIn("ae2");
    public static final GraphicIcon MEKANISM = builtIn("mekanism");
    public static final GraphicIcon FTB_QUESTS = builtIn("ftb_quests");
    public static final GraphicIcon KUBEJS = builtIn("kubejs");
    public static final GraphicIcon GREGTECH = builtIn("gregtech");
    public static final GraphicIcon PLUGIN = builtIn("plugin");
    public static final GraphicIcon GITHUB = builtIn("github");
    public static final GraphicIcon GITHUB_REPO = builtIn("github_repo");
    public static final GraphicIcon GITHUB_PULL_REQUEST = builtIn("github_pull_request");
    public static final GraphicIcon GITHUB_ISSUE = builtIn("github_issue");
    public static final GraphicIcon GITHUB_DISCUSSION = builtIn("github_discussion");
    public static final GraphicIcon GITHUB_ACTIONS = builtIn("github_actions");
    public static final GraphicIcon GITHUB_README = builtIn("github_readme");
    public static final GraphicIcon GITHUB_RELEASE = builtIn("github_release");
    public static final GraphicIcon GITHUB_COMMITS = builtIn("github_commits");
    public static final GraphicIcon LICENSE = builtIn("license");
    public static final GraphicIcon LICENSE_OPEN_SOURCE = builtIn("license_open_source");
    public static final GraphicIcon LICENSE_CLOSED_SOURCE = builtIn("license_closed_source");
    public static final GraphicIcon AUTHORIZATION = builtIn("authorization");
    public static final GraphicIcon CONTRIBUTORS = builtIn("contributors");
    public static final GraphicIcon AFDIAN = builtIn("afdian");
    public static final GraphicIcon SUPPORT = builtIn("support");
    public static final GraphicIcon ACKNOWLEDGMENTS = builtIn("acknowledgments");
    public static final GraphicIcon CHATGPT = builtIn("chatgpt");
    public static final GraphicIcon CLAUDE = builtIn("claude");
    public static final GraphicIcon DEEPSEEK = builtIn("deepseek");
    public static final GraphicIcon GROK = builtIn("grok");
    public static final GraphicIcon GEMINI = builtIn("gemini");
    public static final GraphicIcon QQ = builtIn("qq");
    public static final GraphicIcon WECHAT = builtIn("wechat");
    public static final GraphicIcon X = builtIn("x");
    public static final GraphicIcon EMAIL = builtIn("email");
    public static final GraphicIcon QQ_MAIL = builtIn("qq_mail");
    public static final GraphicIcon ICLOUD_MAIL = builtIn("icloud_mail");
    public static final GraphicIcon GMAIL = builtIn("gmail");
    public static final GraphicIcon DISCORD = builtIn("discord");
    public static final GraphicIcon TELEGRAM = builtIn("telegram");
    public static final GraphicIcon WHATSAPP = builtIn("whatsapp");
    public static final GraphicIcon FACEBOOK = builtIn("facebook");
    public static final GraphicIcon BILIBILI = builtIn("bilibili");
    public static final GraphicIcon DOUYIN = builtIn("douyin");
    public static final GraphicIcon KUAISHOU = builtIn("kuaishou");
    public static final GraphicIcon YOUTUBE = builtIn("youtube");
    public static final GraphicIcon MUSIC = builtIn("music");
    public static final GraphicIcon APP = builtIn("app");
    public static final GraphicIcon QUESTION = builtIn("question");
    public static final GraphicIcon CALCULATE = builtIn("calculate");
    public static final GraphicIcon CPU = builtIn("cpu");
    public static final GraphicIcon GPU = builtIn("gpu");
    public static final GraphicIcon DISK = builtIn("disk");
    public static final GraphicIcon MAP = builtIn("map");
    public static final GraphicIcon AE_CHANNEL = builtIn("ae_channel");
    public static final GraphicIcon MCMOD = builtIn("mcmod");
    public static final GraphicIcon WIKI = builtIn("wiki");
    public static final GraphicIcon PULL_DOWN = builtIn("pull_down");
    public static final GraphicIcon BUBBLE = builtIn("bubble");
    public static final GraphicIcon COMMENT = BUBBLE;
    public static final GraphicIcon CLOSE = builtIn("close");
    public static final GraphicIcon CANCEL = CLOSE;
    public static final GraphicIcon CRITICAL_CANCEL = builtIn("critical_cancel");
    public static final GraphicIcon HOME = builtIn("home");
    public static final GraphicIcon LIST = builtIn("list");
    public static final GraphicIcon WIRELESS = builtIn("wireless");
    public static final GraphicIcon TOPOLOGY = builtIn("topology");
    public static final GraphicIcon STATISTICS = builtIn("statistics");
    public static final GraphicIcon MEMBERS = builtIn("members");
    public static final GraphicIcon CONFIG = builtIn("config");
    public static final GraphicIcon MANAGEMENT = builtIn("management");
    public static final GraphicIcon PERMISSIONS = builtIn("permissions");
    public static final GraphicIcon DETAILS = builtIn("details");
    public static final GraphicIcon MOD = builtIn("mod");
    public static final GraphicIcon TEMPLATE = builtIn("template");
    public static final GraphicIcon DATA = builtIn("data");
    public static final GraphicIcon NETWORK = builtIn("network");
    public static final GraphicIcon SERVER = builtIn("server");
    public static final GraphicIcon DATABASE = builtIn("database");
    public static final GraphicIcon BACKUP = builtIn("backup");
    public static final GraphicIcon MIGRATE = builtIn("migrate");
    public static final GraphicIcon UPGRADE = builtIn("upgrade");
    public static final GraphicIcon SECURITY = builtIn("security");
    public static final GraphicIcon AUDIT = builtIn("audit");
    public static final GraphicIcon REPORT = builtIn("report");
    public static final GraphicIcon QUEUE = builtIn("queue");
    public static final GraphicIcon POLLING = builtIn("polling");
    public static final GraphicIcon ASYNC = builtIn("async");
    public static final GraphicIcon CONCURRENCY = builtIn("concurrency");
    public static final GraphicIcon THREAD = builtIn("thread");
    public static final GraphicIcon PROCESS = builtIn("process");
    public static final GraphicIcon PARALLEL = builtIn("parallel");
    public static final GraphicIcon REWARD = builtIn("reward");
    public static final GraphicIcon SCHEDULER = builtIn("scheduler");
    public static final GraphicIcon TASK = builtIn("task");

    public GraphicIcon {
        Objects.requireNonNull(texture, "texture");
    }

    public GraphicIcon(ResourceLocation texture) {
        this(texture, null, null);
    }

    public GraphicIcon(ResourceLocation texture, GraphicIconShader shader) {
        this(texture, shader, null);
    }

    /** 返回共用同一贴图和动效的新图标。传 null 移除着色器。 */
    public GraphicIcon withShader(GraphicIconShader shader) {
        return new GraphicIcon(texture, shader, motion);
    }

    /** 返回共用同一贴图和着色器的新图标。传 null 移除几何动效。 */
    public GraphicIcon withMotion(GraphicIconMotion motion) {
        return new GraphicIcon(texture, shader, motion);
    }

    /** 按正整数倍缩放，坐标和返回尺寸均使用逻辑 GUI 单位。 */
    public static int size(int scale) {
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
        return Math.multiplyExact(PIXELS, scale);
    }

    /** 在客户端渲染线程绘制，ARGB 色值与外层颜色相乘。 */
    public void draw(GuiGraphics graphics, int x, int y, int scale, int argb) {
        draw(graphics, x, y, scale, argb, UiPreferences.configured());
    }

    /** 在客户端渲染线程绘制，可传入宿主画面的动效偏好。 */
    public void draw(GuiGraphics graphics, int x, int y, int scale, int argb, UiPreferences preferences) {
        int size = size(scale);
        if ((argb >>> 24) == 0) return;

        float[] color = RenderSystem.getShaderColor().clone();
        var previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            graphics.flush();
            if (!depthTest) RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(color[0] * ((argb >>> 16) & 255) / 255f,
                    color[1] * ((argb >>> 8) & 255) / 255f, color[2] * (argb & 255) / 255f,
                    color[3] * (argb >>> 24) / 255f);
            boolean animate = motion != null && !preferences.reducedMotion();
            var pose = graphics.pose();
            if (animate) pose.pushPose();
            try {
                if (animate) {
                    var frame = motion.sample(PageTransition.nowMs(), size, false);
                    float centerX = x + size * 0.5f;
                    float centerY = y + size * 0.5f;
                    pose.translate(centerX, centerY + frame.offsetY(), 0f);
                    pose.mulPose(Axis.ZP.rotationDegrees(frame.rotationDegrees()));
                    pose.scale(frame.scale(), frame.scale(), 1f);
                    pose.translate(-centerX, -centerY, 0f);
                }
                if (shader == null || !shader.draw(graphics, texture, x, y, size, preferences)) {
                    graphics.blit(texture, x, y, size, size, 0f, 0f, PIXELS, PIXELS, PIXELS, PIXELS);
                }
            } finally {
                if (animate) pose.popPose();
            }
        } finally {
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderTexture(0, previousTexture);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (blending) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    private static GraphicIcon builtIn(String name) {
        return new GraphicIcon(ResourceLocation.tryParse("sh_better_ui:textures/gui/display/" + name + ".png"));
    }
}
