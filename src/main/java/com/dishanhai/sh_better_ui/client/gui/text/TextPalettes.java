package com.dishanhai.sh_better_ui.client.gui.text;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/** 山海：移植自 gt_shanhai 的色板资料与取色算法。 */
final class TextPalettes {
    enum Mode { CYCLE, BREATHE, SCAN, STATIC, TWINKLE, DISTORT, WOBBLE, ALL, RANDOM, FULL_RANDOM }

    enum RandomMotion {
        FLOW, REVERSE, TIME_JUMP, WAVE_Y, WAVE_X, SHAKE, CIRCLE, BOUNCE,
        SCAN, BREATHE, CHASE, TWINKLE, GLITCH
    }

    record Palette(int[] rgb, boolean body, Mode mode, boolean obfuscated) {}
    record RandomFrame(int[][] palettes, long now, double fraction, RandomMotion motion,
                       long motionTime, float strength, int[] current, int[] upcoming, double elapsed) {}
    private record PresetOrder(long cycle, int[] indices) {}

    private static final long PRESET_SEED = ThreadLocalRandom.current().nextLong();
    private static final int RANDOM_WINDOW = 1400;
    private static final RandomMotion[] MOTIONS = RandomMotion.values();

    private static volatile PresetOrder recentOrder;
    private static volatile PresetOrder previousOrder;
    private static volatile RandomFrame recentRandomFrame;

    private static final int[] RAINBOW_RGB = {
            0xFF3333, 0xFF4F22, 0xFF6C11, 0xFF8800, 0xFF9F00, 0xFFB500, 0xFFCC00,
            0xBBD211, 0x77D722, 0x33DD33, 0x33DD6C, 0x33DDA4, 0x33DDDD,
            0x33B5E8, 0x338EF4, 0x3366FF, 0x6655F4, 0x9944E8, 0xCC33DD
    };

    private static final int[] SHANHAI_DAWN_RGB = {
            0xFF5577, 0xFF755B, 0xFFAB53, 0xFFDA72, 0xFFF1B0,
            0xFFD48A, 0xFF9E70, 0xFF668B, 0xE861AE, 0xF984A0
    };

    private static final int[] SHANHAI_MIST_RGB = {
            0x3CBF9B, 0x51D4AC, 0x75E4C5, 0xA0F0DD, 0xC9FCF0,
            0xB3F6F4, 0x80E2EE, 0x5DC8D8, 0x48B7BA, 0x3AA994
    };

    private static final int[] SHANHAI_GALAXY_RGB = {
            0x6652CC, 0x7663E4, 0x8C87F7, 0xA6B1FF, 0xBFD5FF, 0x9CCCF4,
            0x88A9E9, 0x967EE6, 0xBB77DC, 0xD17BCC, 0x9D62CB, 0x7655C8
    };

    private static final int[] SHANHAI_PRISM_RGB = {
            0xFF5B8F, 0xFF8462, 0xFFBC54, 0xF2E56C, 0xA3E778, 0x54DCC0,
            0x58C7F4, 0x7EA7FF, 0xB18AFC, 0xE67FE2, 0xFF7AB5
    };

    private static final int[] GOLDEN_RGB = {
            0x995500, 0xAA6600, 0xBB7700, 0xCC8800, 0xDD9300, 0xEE9F00, 0xFFAA00,
            0xFFBB17, 0xFFCC2D, 0xFFDD44, 0xFFE85B, 0xFFF471, 0xFFFF88,
            0xFFFF9F, 0xFFFFB5, 0xFFFFCC, 0xFFFFB5, 0xFFFF9F, 0xFFFF88,
            0xFFF471, 0xFFE85B, 0xFFDD44, 0xFFCC2D, 0xFFBB17, 0xFFAA00,
            0xEE9F00, 0xDD9300, 0xCC8800, 0xBB7700, 0xAA6600, 0x995500
    };

    private static final int[] FIRE_RGB = {
            0x992200, 0xA42800, 0xB02D00, 0xBB3300, 0xC63900, 0xD23E00, 0xDD4400,
            0xE84F00, 0xF45B00, 0xFF6600, 0xFF7D00, 0xFF9300, 0xFFAA00,
            0xFFC11C, 0xFFD739, 0xFFEE55, 0xFFF48E, 0xFFF9C6, 0xFFFFFF,
            0xFFF9C6, 0xFFF48E, 0xFFEE55, 0xFFD739, 0xFFC11C, 0xFFAA00,
            0xFF9300, 0xFF7D00, 0xFF6600, 0xF45B00, 0xE84F00, 0xDD4400,
            0xD23E00, 0xC63900, 0xBB3300, 0xB02D00, 0xA42800, 0x992200
    };

    private static final int[] WATER_RGB = {
            0x004488, 0x004F93, 0x005B9F, 0x0066AA, 0x1177BB, 0x2288CC, 0x3399DD,
            0x44AAE8, 0x55BBF4, 0x66CCFF, 0x7DD7FF, 0x93E3FF, 0xAAEEFF,
            0xC6F4FF, 0xE3F9FF, 0xFFFFFF, 0xE3F9FF, 0xC6F4FF, 0xAAEEFF,
            0x93E3FF, 0x7DD7FF, 0x66CCFF, 0x55BBF4, 0x44AAE8, 0x3399DD,
            0x2288CC, 0x1177BB, 0x0066AA, 0x004F93, 0x004488
    };

    private static final int[] MAGIC_RGB = {
            0x550088, 0x600099, 0x6C00AA, 0x7700BB, 0x8217C6, 0x8E2DD2, 0x9944DD,
            0xA44FE3, 0xB05BE8, 0xBB66EE, 0xC671F4, 0xD27DF9, 0xDD88FF,
            0xE888FF, 0xF488FF, 0xFF88FF, 0xF488FF, 0xE888FF, 0xDD88FF,
            0xD27DF9, 0xC671F4, 0xBB66EE, 0xB05BE8, 0xA44FE3, 0x9944DD,
            0x8E2DD2, 0x8217C6, 0x7700BB, 0x6C00AA, 0x600099, 0x550088
    };

    private static final int[] NATURE_RGB = {
            0x006633, 0x0B7739, 0x17883E, 0x229944, 0x2DAA44, 0x39BB44, 0x44CC44,
            0x5BD244, 0x71D744, 0x88DD44, 0xA4E84A, 0xC1F44F, 0xDDFF55,
            0xC1F44F, 0xA4E84A, 0x88DD44, 0x71D744, 0x5BD244, 0x44CC44,
            0x39BB44, 0x2DAA44, 0x229944, 0x17883E, 0x0B7739, 0x006633
    };

    private static final int[] ELECTRIC_RGB = {
            0xFFDD00, 0xC1DD55, 0x82DDAA, 0x44DDFF, 0x82E8FF, 0xC1F4FF, 0xFFFFFF,
            0xC1F4FF, 0x82E8FF, 0x44DDFF, 0x82DDAA, 0xC1DD55, 0xFFDD00
    };

    private static final int[] ICE_RGB = {
            0x003366, 0x0B4488, 0x1755AA, 0x2266CC, 0x2D77D7, 0x3988E3, 0x4499EE,
            0x5BAAF4, 0x71BBF9, 0x88CCFF, 0xB0DDFF, 0xD7EEFF, 0xFFFFFF,
            0xD7EEFF, 0xB0DDFF, 0x88CCFF, 0x71BBF9, 0x5BAAF4, 0x4499EE,
            0x3988E3, 0x2D77D7, 0x2266CC, 0x1755AA, 0x0B4488, 0x003366
    };

    private static final int[] LAVA_RGB = {
            0x771100, 0x881700, 0x991C00, 0xAA2200, 0xBB2D00, 0xCC3900, 0xDD4400,
            0xE84F00, 0xF45B00, 0xFF6600, 0xFF7D00, 0xFF9300, 0xFFAA00,
            0xFFBB17, 0xFFCC2D, 0xFFDD44, 0xFFE882, 0xFFF4C1, 0xFFFFFF,
            0xFFEEBB, 0xFFDD77, 0xFFCC33, 0xFFB522, 0xFF9F11, 0xFF8800,
            0xF47100, 0xE85B00, 0xDD4400, 0xC13300, 0xA42200, 0x881100
    };

    private static final int[] ULTIMATE_RAINBOW_RGB = {
            0xFF4444, 0xFF5B2D, 0xFF7117, 0xFF8800,
            0xFF9900, 0xFFAA00, 0xFFBB00, 0xFFCC17,
            0xFFDD2D, 0xFFEE44, 0xE3F444, 0xC6F944,
            0xAAFF44, 0x88FF44, 0x66FF44, 0x44FF44,
            0x44FF66, 0x44FF88, 0x44FFAA, 0x44F4C6,
            0x44E8E3, 0x44DDFF, 0x44C1FF, 0x44A4FF,
            0x4488FF, 0x5571FF, 0x665BFF, 0x7744FF,
            0x8E44FF, 0xA444FF, 0xBB44FF, 0xD244FF,
            0xE844FF, 0xFF44FF, 0xFF4FE8, 0xFF5BD2,
            0xFF66BB, 0xFF71AA, 0xFF7D99, 0xFF8888
    };

    private static final int[] SUNSET_RGB = {
            0xFFF8F0, 0xFFF5EE, 0xFFF2EC, 0xFFEFEA, 0xFEECE8, 0xFCE9E6, 0xFAE6E4,
            0xF8E3E2, 0xF6E0E0, 0xF4DDDE, 0xF0DADC, 0xECD7DA, 0xE8D4D8,
            0xE4D1D6, 0xE0CED4, 0xDCCBD2, 0xD8C8D0, 0xD4C5CE, 0xD0C2CC,
            0xCCBFC8, 0xC8BCC4, 0xC4B9C0, 0xC0B6BC, 0xBCB3B8, 0xB8B0B4
    };

    private static final int[] AURORA_RGB = {
            0x33FF44, 0x33EE55, 0x33DD66, 0x33CC77, 0x33BB88, 0x33AA99, 0x3399BB, 0x3388CC, 0x4477DD,
            0x5566EE, 0x7755FF, 0x9944FF, 0xBB33FF, 0x9944FF, 0x7755FF, 0x5566EE, 0x4477DD, 0x3388CC,
            0x3399BB, 0x33AA99, 0x33BB88, 0x33CC77, 0x33DD66, 0x33EE55, 0x33FF44
    };

    private static final int[] CRIMSON_RGB = {
            0x991111, 0xAA1111, 0xBB1111, 0xCC1111, 0xDD2222, 0xEE3333, 0xFF4444, 0xFF5555, 0xFF6666,
            0xFF5555, 0xFF4444, 0xEE3333, 0xDD2222, 0xCC1111, 0xBB1111, 0xAA1111, 0x991111
    };

    private static final int[] NEON_RGB = {
            0xFF33FF, 0xFF55CC, 0xFF7799, 0xFFAA66, 0xFFCC44, 0xAAFF33, 0x77FF44, 0x44FF77, 0x33FFBB,
            0x33FFEE, 0xFFFFFF, 0x33FFEE, 0x33FFBB, 0x44FF77, 0x77FF44, 0xAAFF33, 0xFFCC44, 0xFFAA66,
            0xFF7799, 0xFF55CC, 0xFF33FF
    };

    private static final int[] SAKURA_RGB = {
            0xFF99BB, 0xFFA3C4, 0xFFADCC, 0xFFB7D4, 0xFFC1DD, 0xFFCBE5, 0xFFD5ED, 0xFFDFF0, 0xFFEFF8,
            0xFFFFFF, 0xFFEFF8, 0xFFDFF0, 0xFFD5ED, 0xFFCBE5, 0xFFC1DD, 0xFFB7D4, 0xFFADCC, 0xFFA3C4, 0xFF99BB
    };

    private static final int[] COSMIC_RGB = {
            0x553388, 0x6644AA, 0x7755CC, 0x6688EE, 0x44AAFF, 0x88CCFF, 0xDDF4FF,
            0xFFFFFF, 0xE8D8FF, 0xCCAAFF, 0xAA77FF, 0x8866DD, 0x6644AA, 0x553388
    };

    private static final int[] VOID_RGB = {
            0x3A2255, 0x4A2A66, 0x5A3380, 0x6A4499, 0x7A55AA, 0x9977CC, 0xC8AAEE,
            0xFFFFFF, 0xC8AAEE, 0x9977CC, 0x7A55AA, 0x6A4499, 0x4A2A66, 0x3A2255
    };

    private static final int[] JADE_RGB = {
            0x003322, 0x005533, 0x007744, 0x119955, 0x33BB77, 0x66DDAA, 0xCCFFEE,
            0xFFFFFF, 0xCCFFEE, 0x66DDAA, 0x33BB77, 0x119955, 0x005533
    };

    private static final int[] PLASMA_RGB = {
            0x220033, 0x550066, 0x990088, 0xDD2277, 0xFF5555, 0xFFAA33, 0xFFFF66,
            0xFFFFFF, 0xFFFF66, 0xFFAA33, 0xFF5555, 0xDD2277, 0x990088, 0x550066
    };

    private static final int[] STARLIGHT_RGB = {
            0x7788AA, 0x99AACC, 0xBBC8EE, 0xDDE8FF, 0xFFFFFF, 0xFFF2BB, 0xFFE088,
            0xFFFFFF, 0xDDE8FF, 0xBBC8EE, 0x99AACC, 0x7788AA
    };

    private static final int[] ABYSS_RGB = {
            0x224466, 0x2A5577, 0x336688, 0x4477AA, 0x5588CC, 0x66AAEE, 0x99DDFF,
            0xDDFFFF, 0x99DDFF, 0x66AAEE, 0x5588CC, 0x4477AA, 0x224466
    };

    private static final int[] BODY_GOLDEN_RGB = {
            0x887744, 0x998855, 0xAA9966, 0xBBAA77, 0xCCBB88, 0xDDCC99,
            0xCCBB88, 0xBBAA77, 0xAA9966, 0x998855, 0x887744
    };

    private static final int[] BODY_FIRE_RGB = {
            0x885522, 0x996633, 0xAA7744, 0xBB8855, 0xCC9966,
            0xBB8855, 0xAA7744, 0x996633, 0x885522
    };

    private static final int[] BODY_WATER_RGB = {
            0x446688, 0x557799, 0x6688AA, 0x7799BB, 0x88AACC,
            0x7799BB, 0x6688AA, 0x557799, 0x446688
    };

    private static final int[] BODY_MAGIC_RGB = {
            0x775588, 0x886699, 0x9977AA, 0xAA88BB, 0xBB99CC,
            0xAA88BB, 0x9977AA, 0x886699, 0x775588
    };

    private static final int[] BODY_NATURE_RGB = {
            0x557755, 0x668866, 0x779977, 0x88AA88, 0x99BB99,
            0x88AA88, 0x779977, 0x668866, 0x557755
    };

    private static final int[] BODY_CRIMSON_RGB = {
            0x883333, 0x994444, 0xAA5555, 0xBB6666, 0xCC7777,
            0xBB6666, 0xAA5555, 0x994444, 0x883333
    };

    private static final int[] BODY_SILVER_RGB = {
            0xFFFFFF, 0xF0F0F0, 0xE0E0E0, 0xD0D0D0, 0xC0C0C0, 0xB0B0B0,
            0xA0A0A0, 0x909090, 0x808080, 0x707070, 0x808080, 0x909090,
            0xA0A0A0, 0xB0B0B0, 0xC0C0C0, 0xD0D0D0, 0xE0E0E0, 0xF0F0F0, 0xFFFFFF
    };

    private static final int[] BODY_SUNSET_RGB = {
            0xE8E0DA, 0xE4DCD6, 0xE0D8D2, 0xDCD4CE, 0xD8D0CA,
            0xDCD4CE, 0xE0D8D2, 0xE4DCD6, 0xE8E0DA
    };

    private static final int[] BODY_AURORA_RGB = {
            0x557766, 0x668877, 0x779988, 0x88AA99, 0x99BBAA,
            0x88AA99, 0x779988, 0x668877, 0x557766
    };

    private static final int[] BODY_NEON_RGB = {
            0x885588, 0x996699, 0xAA77AA, 0xBB88BB, 0xCC99CC,
            0xBB88BB, 0xAA77AA, 0x996699, 0x885588
    };

    private static final int[] BODY_ELECTRIC_RGB = {
            0x557788, 0x668899, 0x7799AA, 0x88AABB, 0x99BBCC,
            0x88AABB, 0x7799AA, 0x668899, 0x557788
    };

    private static final int[] BODY_ICE_RGB = {
            0x7799AA, 0x88AABB, 0x99BBCC, 0xAACCDD, 0xBBDDEE,
            0xAACCDD, 0x99BBCC, 0x88AABB, 0x7799AA
    };

    private static final int[] BODY_LAVA_RGB = {
            0x773322, 0x884433, 0x995544, 0xAA6655, 0xBB7766,
            0xAA6655, 0x995544, 0x884433, 0x773322
    };

    private static final int[] BODY_CREAM_RGB = {
            0xD4C5A8, 0xD8C9AE, 0xDCCDB4, 0xE0D1BA, 0xE4D5C0,
            0xE0D1BA, 0xDCCDB4, 0xD8C9AE, 0xD4C5A8
    };

    private static final int[] BODY_AMBER_RGB = {
            0xB89860, 0xC0A068, 0xC8A870, 0xD0B078, 0xD8B880,
            0xD0B078, 0xC8A870, 0xC0A068, 0xB89860
    };

    private static final int[] BODY_SLATE_RGB = {
            0x788898, 0x8090A0, 0x8898A8, 0x90A0B0, 0x98A8B8,
            0x90A0B0, 0x8898A8, 0x8090A0, 0x788898
    };

    private static final int[] BODY_ROSE_RGB = {
            0xB88888, 0xC09090, 0xC89898, 0xD0A0A0, 0xD8A8A8,
            0xD0A0A0, 0xC89898, 0xC09090, 0xB88888
    };

    private static final int[] BODY_MOSS_RGB = {
            0x809870, 0x88A078, 0x90A880, 0x98B088, 0xA0B890,
            0x98B088, 0x90A880, 0x88A078, 0x809870
    };

    private static final Map<String, Palette> BUILT_INS = builtIns();
    private static volatile int[][] flowing = BUILT_INS.values().stream()
            .filter(palette -> palette.mode() == Mode.CYCLE)
            .map(Palette::rgb).distinct().toArray(int[][]::new);

    private TextPalettes() {}

    static Palette get(String name) {
        return BUILT_INS.get(name);
    }

    static Set<String> names() {
        return BUILT_INS.keySet();
    }

    static void addFlowingPalette(Palette palette) {
        int[][] existing = flowing;
        int[][] next = Arrays.copyOf(existing, existing.length + 1);
        next[existing.length] = palette.rgb();
        flowing = next;
    }

    private static Map<String, Palette> builtIns() {
        Map<String, Palette> palettes = new LinkedHashMap<>();
        add(palettes, "ultimate", ULTIMATE_RAINBOW_RGB, false, Mode.CYCLE, false);
        add(palettes, "rainbow", RAINBOW_RGB, false, Mode.CYCLE, false);
        add(palettes, "obfuscatedrainbow", RAINBOW_RGB, false, Mode.CYCLE, true);
        add(palettes, "golden", GOLDEN_RGB, false, Mode.CYCLE, false);
        add(palettes, "fire", FIRE_RGB, false, Mode.CYCLE, false);
        add(palettes, "water", WATER_RGB, false, Mode.CYCLE, false);
        add(palettes, "magic", MAGIC_RGB, false, Mode.CYCLE, false);
        add(palettes, "nature", NATURE_RGB, false, Mode.CYCLE, false);
        add(palettes, "electric", ELECTRIC_RGB, false, Mode.CYCLE, false);
        add(palettes, "ice", ICE_RGB, false, Mode.CYCLE, false);
        add(palettes, "lava", LAVA_RGB, false, Mode.CYCLE, false);
        add(palettes, "sunset", SUNSET_RGB, false, Mode.CYCLE, false);
        add(palettes, "aurora", AURORA_RGB, false, Mode.CYCLE, false);
        add(palettes, "crimson", CRIMSON_RGB, false, Mode.CYCLE, false);
        add(palettes, "neon", NEON_RGB, false, Mode.CYCLE, false);
        add(palettes, "sakura", SAKURA_RGB, false, Mode.CYCLE, false);
        add(palettes, "cosmic", COSMIC_RGB, false, Mode.CYCLE, false);
        add(palettes, "void", VOID_RGB, false, Mode.CYCLE, false);
        add(palettes, "jade", JADE_RGB, false, Mode.CYCLE, false);
        add(palettes, "plasma", PLASMA_RGB, false, Mode.CYCLE, false);
        add(palettes, "starlight", STARLIGHT_RGB, false, Mode.CYCLE, false);
        add(palettes, "abyss", ABYSS_RGB, false, Mode.CYCLE, false);
        add(palettes, "shanhai_dawn", SHANHAI_DAWN_RGB, false, Mode.CYCLE, false);
        add(palettes, "shanhai_mist", SHANHAI_MIST_RGB, false, Mode.CYCLE, false);
        add(palettes, "shanhai_galaxy", SHANHAI_GALAXY_RGB, false, Mode.CYCLE, false);
        add(palettes, "shanhai_prism", SHANHAI_PRISM_RGB, false, Mode.CYCLE, false);
        palettes.put("all", new Palette(new int[0], false, Mode.ALL, false));
        alias(palettes, "quan", "all");
        alias(palettes, "全", "all");
        palettes.put("random", new Palette(new int[0], false, Mode.RANDOM, false));
        alias(palettes, "rand", "random");
        alias(palettes, "随机", "random");
        palettes.put("fullrandom", new Palette(new int[0], false, Mode.FULL_RANDOM, false));
        alias(palettes, "full_random", "fullrandom");
        alias(palettes, "chaos", "fullrandom");
        alias(palettes, "全随机", "fullrandom");
        alias(palettes, "完全随机", "fullrandom");
        add(palettes, "body_golden", BODY_GOLDEN_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_fire", BODY_FIRE_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_water", BODY_WATER_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_magic", BODY_MAGIC_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_nature", BODY_NATURE_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_crimson", BODY_CRIMSON_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_silver", BODY_SILVER_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_sunset", BODY_SUNSET_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_aurora", BODY_AURORA_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_neon", BODY_NEON_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_electric", BODY_ELECTRIC_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_ice", BODY_ICE_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_lava", BODY_LAVA_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_cream", BODY_CREAM_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_amber", BODY_AMBER_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_slate", BODY_SLATE_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_rose", BODY_ROSE_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_moss", BODY_MOSS_RGB, true, Mode.CYCLE, false);
        add(palettes, "body_cream_breathe", BODY_CREAM_RGB, true, Mode.BREATHE, false);
        add(palettes, "body_cream_scan", BODY_CREAM_RGB, true, Mode.SCAN, false);
        add(palettes, "body_cream_static", BODY_CREAM_RGB, true, Mode.STATIC, false);
        add(palettes, "body_cream_twinkle", BODY_CREAM_RGB, true, Mode.TWINKLE, false);
        add(palettes, "golden_breathe", GOLDEN_RGB, false, Mode.BREATHE, false);
        add(palettes, "golden_static", GOLDEN_RGB, false, Mode.STATIC, false);
        add(palettes, "ultimaterainbow_distort", ULTIMATE_RAINBOW_RGB, false, Mode.DISTORT, false);
        add(palettes, "body_cream_distort", BODY_CREAM_RGB, true, Mode.DISTORT, false);
        add(palettes, "body_silver_distort", BODY_SILVER_RGB, true, Mode.DISTORT, false);
        add(palettes, "ultimaterainbow_wobble", ULTIMATE_RAINBOW_RGB, false, Mode.WOBBLE, false);
        add(palettes, "body_cream_wobble", BODY_CREAM_RGB, true, Mode.WOBBLE, false);
        add(palettes, "body_silver_wobble", BODY_SILVER_RGB, true, Mode.WOBBLE, false);
        add(palettes, "golden_wobble", GOLDEN_RGB, false, Mode.WOBBLE, false);
        add(palettes, "fire_wobble", FIRE_RGB, false, Mode.WOBBLE, false);
        alias(palettes, "终极", "ultimate");
        alias(palettes, "终极彩虹", "ultimate");
        alias(palettes, "彩虹", "rainbow");
        alias(palettes, "混淆", "obfuscatedrainbow");
        alias(palettes, "混淆彩虹", "obfuscatedrainbow");
        alias(palettes, "金", "golden");
        alias(palettes, "金色", "golden");
        alias(palettes, "火", "fire");
        alias(palettes, "火焰", "fire");
        alias(palettes, "水", "water");
        alias(palettes, "水流", "water");
        alias(palettes, "魔", "magic");
        alias(palettes, "魔法", "magic");
        alias(palettes, "自然", "nature");
        alias(palettes, "电", "electric");
        alias(palettes, "电光", "electric");
        alias(palettes, "电流", "electric");
        alias(palettes, "冰", "ice");
        alias(palettes, "冰霜", "ice");
        alias(palettes, "熔岩", "lava");
        alias(palettes, "日落", "sunset");
        alias(palettes, "极光", "aurora");
        alias(palettes, "猩红", "crimson");
        alias(palettes, "霓虹", "neon");
        alias(palettes, "樱", "sakura");
        alias(palettes, "樱花", "sakura");
        alias(palettes, "宇宙", "cosmic");
        alias(palettes, "星云", "cosmic");
        alias(palettes, "虚空", "void");
        alias(palettes, "玉", "jade");
        alias(palettes, "青玉", "jade");
        alias(palettes, "等离子", "plasma");
        alias(palettes, "星辉", "starlight");
        alias(palettes, "深渊", "abyss");
        alias(palettes, "山海朝霞", "shanhai_dawn");
        alias(palettes, "山海青岚", "shanhai_mist");
        alias(palettes, "山海青嵐", "shanhai_mist");
        alias(palettes, "山海星河", "shanhai_galaxy");
        alias(palettes, "山海琉彩", "shanhai_prism");
        alias(palettes, "正文金", "body_golden");
        alias(palettes, "正文金色", "body_golden");
        alias(palettes, "正文火", "body_fire");
        alias(palettes, "正文火焰", "body_fire");
        alias(palettes, "正文水", "body_water");
        alias(palettes, "正文水流", "body_water");
        alias(palettes, "正文魔", "body_magic");
        alias(palettes, "正文魔法", "body_magic");
        alias(palettes, "正文自然", "body_nature");
        alias(palettes, "正文猩红", "body_crimson");
        alias(palettes, "银", "body_silver");
        alias(palettes, "银色", "body_silver");
        alias(palettes, "正文银", "body_silver");
        alias(palettes, "正文银色", "body_silver");
        alias(palettes, "正文日落", "body_sunset");
        alias(palettes, "正文极光", "body_aurora");
        alias(palettes, "正文霓虹", "body_neon");
        alias(palettes, "正文电", "body_electric");
        alias(palettes, "正文电光", "body_electric");
        alias(palettes, "正文冰", "body_ice");
        alias(palettes, "正文冰霜", "body_ice");
        alias(palettes, "正文熔岩", "body_lava");
        alias(palettes, "奶油", "body_cream");
        alias(palettes, "正文奶油", "body_cream");
        alias(palettes, "琥珀", "body_amber");
        alias(palettes, "正文琥珀", "body_amber");
        alias(palettes, "板岩", "body_slate");
        alias(palettes, "灰板岩", "body_slate");
        alias(palettes, "正文板岩", "body_slate");
        alias(palettes, "玫瑰", "body_rose");
        alias(palettes, "正文玫瑰", "body_rose");
        alias(palettes, "苔", "body_moss");
        alias(palettes, "苔藓", "body_moss");
        alias(palettes, "正文苔", "body_moss");
        alias(palettes, "正文苔藓", "body_moss");
        alias(palettes, "隨機", "random");
        alias(palettes, "全隨機", "fullrandom");
        alias(palettes, "完全隨機", "fullrandom");
        return Collections.unmodifiableMap(palettes);
    }

    private static void add(Map<String, Palette> palettes, String name, int[] rgb, boolean body,
                            Mode mode, boolean obfuscated) {
        Palette palette = new Palette(rgb, body, mode, obfuscated);
        palettes.put(name, palette);
        if (body && name.startsWith("body_") && mode == Mode.CYCLE) {
            palettes.put(name.replace("body_", "body"), palette);
        }
    }

    private static void alias(Map<String, Palette> palettes, String alias, String canonical) {
        palettes.put(alias, palettes.get(canonical));
    }

    static int color(Palette palette, int index, int length, long time) {
        int[] rgb = palette.rgb();
        long interval = interval(palette.body(), length);
        return switch (palette.mode()) {
            case ALL -> sampleAll(index, time);
            case RANDOM -> sampleRandom(randomFrame(time), index, length);
            case FULL_RANDOM -> sampleFullRandom(index, time);
            case STATIC -> rgb[rgb.length / 2];
            case BREATHE -> rgb[(int) (Math.floorMod(time, interval * rgb.length) / interval)];
            case SCAN -> scan(rgb, index, length, time);
            case TWINKLE -> twinkle(rgb, index, interval * (palette.body() ? 3 : 4), time);
            case DISTORT -> rgb[Math.floorMod(Math.floorDiv(time + index * 137L, 50L), rgb.length)];
            case CYCLE, WOBBLE -> sampleTimed(rgb, index, interval, time);
        };
    }

    private static long interval(boolean body, int length) {
        if (body) return 200;
        if (length <= 5) return 80;
        if (length <= 8) return 60;
        if (length <= 10) return 50;
        return 65;
    }

    private static int scan(int[] rgb, int index, int length, long time) {
        int position = (int) (Math.floorMod(time, 1200L) * length / 1200L);
        int brightening = Math.max(0, 100 - 50 * Math.abs(position - index));
        int base = rgb[rgb.length / 4];
        return (Math.min(255, channel8(base, 16) + brightening) << 16)
                | (Math.min(255, channel8(base, 8) + brightening) << 8)
                | Math.min(255, channel8(base, 0) + brightening);
    }

    private static int twinkle(int[] rgb, int index, long interval, long time) {
        double phase = (double) Math.floorMod(time + index * 3000L, interval * 4) / interval;
        return rgb[(int) (Math.abs(Math.sin(phase * 0.5)) * (rgb.length - 1))];
    }

    private static int sampleTimed(int[] palette, int index, long interval, long time) {
        double phase = (double) Math.floorMod(time, interval * palette.length) / interval;
        int start = Math.floorMod((int) phase + index, palette.length);
        return lerpSrgb(palette[start], palette[(start + 1) % palette.length], phase - (int) phase);
    }

    /** 全按注册顺序访问每套流动色板，轮长变化，末段渐变衔接。 */
    private static int sampleAll(int index, long time) {
        int[][] presets = flowing;
        double position = presetPosition(time / 800.0 + index * 0.18, presets.length);
        long slot = (long) Math.floor(position);
        int current = Math.floorMod(slot, presets.length);
        double local = position - slot;
        int color = sampleFraction(presets[current], local);
        if (local < 0.75) return color;
        return lerpSrgb(color, presets[(current + 1) % presets.length][0], smoothstep((local - 0.75) / 0.25));
    }

    /** 随机按轮洗牌，同轮无重复，轮长变化，整个字串使用同一组效果。 */
    static RandomFrame randomFrame(long time) {
        int[][] presets = flowing;
        RandomFrame cached = recentRandomFrame;
        if (cached != null && cached.now() == time && cached.palettes() == presets) return cached;
        int count = presets.length;
        double position = presetPosition(time / (double) RANDOM_WINDOW, count);
        long slot = (long) Math.floor(position);
        double fraction = position - slot;
        RandomMotion motion = MOTIONS[pickPreset(slot, MOTIONS.length)];
        long motionTime = switch (motion) {
            case REVERSE -> -time;
            case TIME_JUMP -> time + (long) Math.floor(fraction * 8.0) * 30_000L;
            default -> time;
        };
        float strength = (float) (smoothstep(fraction / 0.15) * smoothstep((1 - fraction) / 0.25));
        double elapsed = 0;
        if (motion == RandomMotion.REVERSE) {
            long cycle = Math.floorDiv(slot, count);
            double cycleStart = cycleStart(cycle, count);
            double cycleEnd = cycleStart(cycle + 1, count);
            double slotStart = cycleStart + (cycleEnd - cycleStart) * Math.floorMod(slot, count) / count;
            elapsed = time - slotStart * RANDOM_WINDOW;
        }
        int[] current = presets[pickPreset(slot, count)];
        int[] upcoming = fraction < 0.72 ? current : presets[pickPreset(slot + 1, count)];
        RandomFrame frame = new RandomFrame(presets, time, fraction, motion, motionTime, strength,
                current, upcoming, elapsed);
        recentRandomFrame = frame;
        return frame;
    }

    static int sampleRandom(RandomFrame frame, int index, int length) {
        int current = sampleRandomStop(frame, frame.current(), index, length);
        if (frame.fraction() < 0.72) return current;
        int upcoming = sampleRandomStop(frame, frame.upcoming(), index, length);
        return lerpSrgb(current, upcoming, smoothstep((frame.fraction() - 0.72) / 0.28));
    }

    private static int sampleRandomStop(RandomFrame frame, int[] palette, int index, int length) {
        long interval = interval(false, length);
        if (frame.motion() != RandomMotion.REVERSE && frame.motion() != RandomMotion.TIME_JUMP) {
            return sampleTimed(palette, index, interval, frame.now());
        }
        long cycle = interval * palette.length;
        double phase = Math.floorMod(frame.now(), cycle) / (double) cycle;
        double delta = frame.motion() == RandomMotion.REVERSE ? -2.0 * frame.elapsed() / cycle
                : Math.floor(frame.fraction() * 8.0) * 0.38196601125;
        return sampleFraction(palette, phase + delta * frame.strength() + (double) index / palette.length);
    }

    static int pickPreset(long slot, int count) {
        long cycle = Math.floorDiv(slot, count);
        PresetOrder order = recentOrder;
        if (order == null || order.cycle() != cycle || order.indices().length != count) {
            order = previousOrder;
            if (order == null || order.cycle() != cycle || order.indices().length != count) {
                int[] indices = shuffle(cycle, count);
                if (count > 2 && indices[0] == shuffle(cycle - 1, count)[count - 1]) {
                    int first = indices[0];
                    indices[0] = indices[1];
                    indices[1] = first;
                }
                order = new PresetOrder(cycle, indices);
                previousOrder = recentOrder;
                recentOrder = order;
            }
        }
        return order.indices()[Math.floorMod(slot, count)];
    }

    private static int[] shuffle(long cycle, int count) {
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) indices[i] = i;
        SplittableRandom random = new SplittableRandom(PRESET_SEED ^ (count <= 2 ? 0L : cycle * 0x9E3779B97F4A7C15L));
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int value = indices[i];
            indices[i] = indices[j];
            indices[j] = value;
        }
        return indices;
    }

    private static double cycleStart(long cycle, int count) {
        if (cycle == 0) return 0;
        long hash = PRESET_SEED ^ (cycle * 0x9E3779B97F4A7C15L);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (double) cycle * count + ((hash >>> 11) * 0x1.0p-53 - 0.5) * count * 0.25;
    }

    private static double presetPosition(double travel, int count) {
        long cycle = (long) Math.floor(travel / count);
        double start = cycleStart(cycle, count);
        double end = cycleStart(cycle + 1, count);
        if (travel < start) {
            end = start;
            start = cycleStart(--cycle, count);
        } else if (travel >= end) {
            start = end;
            end = cycleStart(++cycle + 1, count);
        }
        return (double) cycle * count + (travel - start) * count / (end - start);
    }

    private static int sampleFullRandom(int index, long time) {
        long slot = Math.floorDiv(time, 480L);
        double fade = Math.floorMod(time, 480L) / 480.0;
        return lerpSrgb(vividHashColor(slot, index), vividHashColor(slot + 1, index), smoothstep(fade));
    }

    private static int sampleFraction(int[] palette, double fraction) {
        double position = (fraction - Math.floor(fraction)) * palette.length;
        int start = Math.min((int) position, palette.length - 1);
        return lerpSrgb(palette[start], palette[(start + 1) % palette.length], position - start);
    }

    private static int vividHashColor(long slot, int index) {
        long hash = slot * 0x9E3779B97F4A7C15L ^ (index * 0xBF58476D1CE4E5B9L);
        hash ^= hash >>> 30;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 27;
        double hue = (hash & 0xFFFF) / 65535.0 * 6.0;
        double saturation = 0.72 + ((hash >>> 16) & 0xFF) / 255.0 * 0.28;
        double value = 0.88 + ((hash >>> 24) & 0xFF) / 255.0 * 0.12;
        int sector = (int) hue;
        double fraction = hue - sector;
        double min = value * (1.0 - saturation);
        double decreasing = value * (1.0 - saturation * fraction);
        double increasing = value * (1.0 - saturation * (1.0 - fraction));
        return switch (sector % 6) {
            case 0 -> rgb(value, increasing, min);
            case 1 -> rgb(decreasing, value, min);
            case 2 -> rgb(min, value, increasing);
            case 3 -> rgb(min, decreasing, value);
            case 4 -> rgb(increasing, min, value);
            default -> rgb(value, min, decreasing);
        };
    }

    private static int lerpSrgb(int from, int to, double amount) {
        if (amount <= 0.0) return from;
        if (amount >= 1.0) return to;
        return rgb(blendChannel(from, to, 16, amount), blendChannel(from, to, 8, amount),
                blendChannel(from, to, 0, amount));
    }

    private static int channel8(int color, int shift) {
        return (color >> shift) & 0xFF;
    }

    private static double blendChannel(int from, int to, int shift, double amount) {
        double linear = linearize(channel8(from, shift)) * (1 - amount) + linearize(channel8(to, shift)) * amount;
        return linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
    }

    private static double linearize(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int rgb(double red, double green, double blue) {
        return ((int) Math.round(red * 255) << 16) | ((int) Math.round(green * 255) << 8)
                | (int) Math.round(blue * 255);
    }

    private static double smoothstep(double amount) {
        double t = Math.max(0, Math.min(amount, 1));
        return t * t * (3 - 2 * t);
    }
}
