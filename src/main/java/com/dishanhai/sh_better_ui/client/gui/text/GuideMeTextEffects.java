package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/** 山海：将 GuideME 的布局片段关联到它自己的样式，按字符串对象身份传递。 */
public final class GuideMeTextEffects {
    private static final ThreadLocal<Run> CONSTRUCTING = new ThreadLocal<>();
    private static final ThreadLocal<Run> DRAWING = new ThreadLocal<>();

    private GuideMeTextEffects() {}

    private record Run(String text, Component styled) {}

    public static final class Layout {
        private final Component styled;
        private final String clean;
        private int cursor;

        public Layout(Component styled) {
            this.styled = styled;
            this.clean = styled.getString();
        }

        public String text() {
            return clean;
        }

        public void prepareRun(String text) {
            int start = clean.indexOf(text, cursor);
            if (start < 0) return;
            int end = start + text.length();
            cursor = end;
            int[] offset = {0};
            var part = Component.empty();
            styled.visit((style, segment) -> {
                int from = Math.max(0, start - offset[0]);
                int to = Math.min(segment.length(), end - offset[0]);
                if (from < to) part.append(Component.literal(segment.substring(from, to)).setStyle(style));
                offset[0] += segment.length();
                return offset[0] >= end ? Optional.of(Boolean.TRUE) : Optional.empty();
            }, Style.EMPTY);
            CONSTRUCTING.set(new Run(text, part));
        }
    }

    public static Component takeRun(String text) {
        Run run = CONSTRUCTING.get();
        if (run == null || run.text() != text) return null;
        CONSTRUCTING.remove();
        return run.styled();
    }

    public static String beginDraw(String text, Component styled) {
        String argument = new String(text);
        DRAWING.set(new Run(argument, styled));
        return argument;
    }

    public static Component applyTo(Component source) {
        Run run = DRAWING.get();
        if (run == null || source.getContents().visit(text -> run.text() == text
                ? Optional.of(Boolean.TRUE) : Optional.empty()).isEmpty()) return source;
        DRAWING.remove();
        return run.styled().copy().setStyle(source.getStyle());
    }

    public static void endDraw() {
        DRAWING.remove();
    }
}
