package com.dishanhai.sh_better_ui.client.gui.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.dishanhai.sh_better_ui.client.gui.search.SearchQuery.normalize;

/** 山海：供搜索框与自绘界面使用的本地搜索快照。数据或显示语言变化时由调用方替换。 */
public final class SearchIndex<T> {
    private static final int NO_LITERAL_MATCH = 3;
    private static final int RANK_COUNT = 7;

    public record Entry<T>(T value, String name, List<String> ids, List<String> aliases,
                           String modName, List<String> tags) {
        /** 模组 ID 自动取自 ids 的命名空间；旧调用方无需提供模组名称或 tags。 */
        public Entry(T value, String name, List<String> ids, List<String> aliases) {
            this(value, name, ids, aliases, "", List.of());
        }

        public Entry {
            Objects.requireNonNull(value, "value");
            if (normalize(name).isEmpty()) throw new IllegalArgumentException("name must contain searchable text");
            ids = List.copyOf(ids);
            aliases = List.copyOf(aliases);
            Objects.requireNonNull(modName, "modName");
            tags = List.copyOf(tags);
        }
    }

    public record Suggestion<T>(Entry<T> entry, String completion, String label) {
        public Suggestion(Entry<T> entry, String completion) {
            this(entry, completion, entry.name());
        }

        /** 返回可直接追加的字面前缀后缀。别名、分词和包含命中可能没有后缀。 */
        public String suffixFor(String input) {
            if (input.isEmpty() || completion.length() <= input.length()
                    || !completion.regionMatches(true, 0, input, 0, input.length())) return "";
            return completion.substring(input.length());
        }
    }

    private record Prepared<T>(Entry<T> entry, String name, List<String> ids, List<String> aliases,
                               List<String> pinyinKeys, List<String> mods, String modName,
                               List<String> modPinyinKeys, List<String> tags) {
        static <T> Prepared<T> of(Entry<T> entry, Map<String, List<String>> modPinyin) {
            String name = normalize(entry.name());
            List<String> ids = entry.ids().stream().map(SearchQuery::normalize).toList();
            List<String> aliases = entry.aliases().stream().map(SearchQuery::normalize).toList();
            List<String> pinyin = new ArrayList<>(PinyinSearch.keys(name));
            aliases.forEach(alias -> pinyin.addAll(PinyinSearch.keys(alias)));
            List<String> mods = ids.stream().filter(id -> id.indexOf(':') > 0)
                    .map(id -> id.substring(0, id.indexOf(':'))).distinct().toList();
            String modName = normalize(entry.modName());
            List<String> tags = entry.tags().stream().map(SearchQuery::normalize)
                    .filter(tag -> !tag.isEmpty()).distinct().toList();
            return new Prepared<>(entry, name, ids, aliases, List.copyOf(pinyin), mods, modName,
                    modPinyin.computeIfAbsent(modName, PinyinSearch::keys), tags);
        }

        boolean matches(List<SearchQuery.Token> tokens, boolean requiresPinyin) {
            boolean usedPinyin = false;
            for (SearchQuery.Token token : tokens) {
                String literal = token.text();
                if (token.kind() == SearchQuery.Kind.MOD) {
                    if (mods.stream().noneMatch(mod -> modRank(mod, token) < 4)) return false;
                    continue;
                }
                if (token.kind() == SearchQuery.Kind.TAG) {
                    if (tags.stream().noneMatch(tag -> tag.contains(literal))) return false;
                    continue;
                }
                if (name.contains(literal) || ids.stream().anyMatch(id -> id.contains(literal))
                        || aliases.stream().anyMatch(alias -> alias.contains(literal))) continue;
                if (token.pinyin().isEmpty() || pinyinKeys.stream().noneMatch(key -> key.contains(token.pinyin()))) {
                    return false;
                }
                usedPinyin = true;
            }
            return usedPinyin || !requiresPinyin;
        }

        int modRank(String mod, SearchQuery.Token token) {
            if (token.text().isEmpty()) return 0;
            int rank = Math.min(matchRank(mod, token.text()), matchRank(modName, token.text()));
            if (rank < NO_LITERAL_MATCH) return rank;
            return !token.pinyin().isEmpty() && modPinyinKeys.stream().anyMatch(key -> key.contains(token.pinyin()))
                    ? 3 : 4;
        }
    }

    private final List<Prepared<T>> entries;

    public SearchIndex(List<Entry<T>> entries) {
        Map<String, List<String>> modPinyin = new HashMap<>();
        this.entries = entries.stream().map(entry -> Prepared.of(entry, modPinyin)).toList();
    }

    public List<Entry<T>> search(String query) {
        return search(query, value -> true);
    }

    /** 普通词匹配名称、ID、别名或拼音；@ 筛选模组，# 筛选 tag。各词同时成立，结果保留索引顺序。 */
    public List<Entry<T>> search(String query, Predicate<? super T> scope) {
        SearchQuery prepared = SearchQuery.of(query);
        return entries.stream().filter(entry -> scope.test(entry.entry().value()) && entry.matches(prepared.tokens(), false))
                .map(Prepared::entry).toList();
    }

    Set<T> searchValuesByPinyin(String query) {
        SearchQuery prepared = SearchQuery.of(query);
        if (prepared.tokens().isEmpty()) return Set.of();
        return entries.stream().filter(entry -> entry.matches(prepared.tokens(), true))
                .map(entry -> entry.entry().value()).collect(Collectors.toUnmodifiableSet());
    }

    public List<Suggestion<T>> suggest(String query, int limit) {
        return suggest(query, value -> true, limit);
    }

    /** 普通词按名称、ID、其余匹配排序；末词为 @ 或 # 时补全去重后的模组或 tag。 */
    public List<Suggestion<T>> suggest(String query, Predicate<? super T> scope, int limit) {
        return suggest(query, scope, limit, completion -> true);
    }

    List<Suggestion<T>> suggest(String query, Predicate<? super T> scope, int limit,
                                Predicate<String> acceptsCompletion) {
        SearchQuery prepared = SearchQuery.of(query);
        if (limit <= 0 || prepared.tokens().isEmpty()) return List.of();
        if (prepared.tokens().getLast().kind() != SearchQuery.Kind.TEXT) {
            return suggestFilters(prepared, scope, limit, acceptsCompletion);
        }
        String text = prepared.hasFilters() ? prepared.tokens().getLast().text() : prepared.text();

        List<List<Suggestion<T>>> ranked = new ArrayList<>(RANK_COUNT);
        for (int i = 0; i < RANK_COUNT; i++) ranked.add(new ArrayList<>());
        for (Prepared<T> candidate : entries) {
            Entry<T> entry = candidate.entry();
            if (!scope.test(entry.value()) || !candidate.matches(prepared.tokens(), false)) continue;

            String completion = prepared.completeText(entry.name());
            boolean fillable = acceptsCompletion.test(completion);
            int rank = fillable ? matchRank(candidate.name(), text) : NO_LITERAL_MATCH;
            if (rank == NO_LITERAL_MATCH) {
                int idRank = NO_LITERAL_MATCH;
                for (int i = 0; i < candidate.ids().size(); i++) {
                    int next = matchRank(candidate.ids().get(i), text);
                    String id = prepared.completeText(entry.ids().get(i));
                    if (next < idRank && acceptsCompletion.test(id)) {
                        idRank = next;
                        completion = id;
                        fillable = true;
                    }
                }
                rank += idRank;
            }
            List<Suggestion<T>> bucket = ranked.get(rank);
            if (bucket.size() < limit && fillable) {
                bucket.add(new Suggestion<>(entry, completion));
            }
            if (rank == 0 && bucket.size() == limit) break;
        }

        List<Suggestion<T>> result = new ArrayList<>();
        for (List<Suggestion<T>> bucket : ranked) {
            int count = Math.min(bucket.size(), limit - result.size());
            result.addAll(bucket.subList(0, count));
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    private record RankedSuggestion<T>(int rank, Suggestion<T> suggestion) {}

    private List<Suggestion<T>> suggestFilters(SearchQuery query, Predicate<? super T> scope, int limit,
                                               Predicate<String> acceptsCompletion) {
        var token = query.tokens().getLast();
        var preceding = query.tokens().subList(0, query.tokens().size() - 1);
        var unique = new LinkedHashMap<String, RankedSuggestion<T>>();
        boolean mod = token.kind() == SearchQuery.Kind.MOD;
        for (Prepared<T> candidate : entries) {
            if (!scope.test(candidate.entry().value()) || !candidate.matches(preceding, false)) continue;
            for (String value : mod ? candidate.mods() : candidate.tags()) {
                int rank = mod ? candidate.modRank(value, token) : matchRank(value, token.text());
                if (rank >= (mod ? 4 : NO_LITERAL_MATCH)) continue;
                String label = (mod ? "@" : "#") + value;
                String completion = query.replaceLast(label);
                if (!acceptsCompletion.test(completion)) continue;
                var previous = unique.get(label);
                if (previous == null || rank < previous.rank()) {
                    unique.put(label, new RankedSuggestion<>(rank, new Suggestion<>(candidate.entry(), completion, label)));
                }
            }
        }
        return unique.values().stream().sorted(Comparator.comparingInt(RankedSuggestion::rank))
                .limit(limit).map(RankedSuggestion::suggestion).toList();
    }

    private static int matchRank(String text, String query) {
        if (text.equals(query)) return 0;
        if (text.startsWith(query)) return 1;
        return text.contains(query) ? 2 : NO_LITERAL_MATCH;
    }
}
