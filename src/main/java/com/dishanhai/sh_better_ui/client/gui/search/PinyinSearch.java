package com.dishanhai.sh_better_ui.client.gui.search;

import com.ibm.icu.text.Transliterator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 山海：将汉字片段转换为全拼和首字母检索键。 */
final class PinyinSearch {
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}+");
    private static final Pattern TONE_MARKS = Pattern.compile("[\\u0300\\u0301\\u0304\\u030c]");

    private PinyinSearch() {}

    static List<String> keys(String text) {
        var han = HAN.matcher(text);
        if (!han.find()) return List.of();

        List<String> keys = new ArrayList<>();
        do {
            String reading = fold(HanLatin.INSTANCE.transliterate(han.group()));
            String full = reading.replace(" ", "");
            StringBuilder initials = new StringBuilder();
            for (String syllable : reading.split(" ")) {
                if (!syllable.isEmpty()) initials.appendCodePoint(syllable.codePointAt(0));
            }
            keys.add(full);
            if (!full.contentEquals(initials)) keys.add(initials.toString());
        } while (han.find());
        return keys;
    }

    static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return TONE_MARKS.matcher(decomposed.replace("u\u0308", "v")).replaceAll("");
    }

    private static final class HanLatin {
        private static final Transliterator INSTANCE = Transliterator.getInstance("Han-Latin");
    }
}
