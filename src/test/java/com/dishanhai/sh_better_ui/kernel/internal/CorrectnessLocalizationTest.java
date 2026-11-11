// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class CorrectnessLocalizationTest {
    @Test void diagnosticAndInspectorKeysHaveThreeAlignedLocalesAndPlaceholders() throws Exception {
        var english = read("en_us");
        var expected = keys(english);
        assertTrue(expected.size() >= 100);
        for (var locale : new String[] {"zh_cn", "zh_tw"}) {
            var translated = read(locale);
            assertEquals(expected, keys(translated), locale);
            for (var key : expected) {
                assertFalse(translated.get(key).getAsString().isBlank(), key);
                assertEquals(placeholders(english.get(key).getAsString()), placeholders(translated.get(key).getAsString()), key);
            }
        }
    }
    private static JsonObject read(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/sh_better_ui/lang/" + locale + ".json"))).getAsJsonObject();
    }
    private static Set<String> keys(JsonObject source) {
        var result = new TreeSet<String>();
        source.keySet().stream().filter(key -> key.startsWith("sh_better_ui.correctness.")).forEach(result::add);
        return result;
    }
    private static java.util.List<String> placeholders(String text) {
        return Pattern.compile("%(?:[0-9]+\\$)?[sdf]").matcher(text).results().map(match -> match.group()).toList();
    }
}
