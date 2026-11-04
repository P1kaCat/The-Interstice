package com.interstice.client.ui;

import com.interstice.client.config.ClientConfig;
import com.interstice.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Settings screen. Deliberately not a vanilla options list: a single dark
 * column, letter-spaced headings, quiet grey labels, values on the right, a
 * thin animated hover rule and inline sliders. Every row writes straight into
 * the live config, so changes are visible immediately behind the panel.
 */
public class IntersticeConfigScreen extends Screen {
    private static final int ROW = 18, HEADER = 26;
    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private double scroll;
    private Entry dragging;
    private float open;

    private abstract static class Entry {
        final String label;
        float hover;
        int y;

        Entry(String label) { this.label = label; }

        boolean header() { return false; }
        String value() { return ""; }
        void click(int button, double fx) {}
        void drag(double fx) {}
        double fill() { return -1; }
    }

    private static final class Header extends Entry {
        Header(String l) { super(l); }
        @Override boolean header() { return true; }
    }

    private static final class Toggle extends Entry {
        final BooleanSupplier get; final Consumer<Boolean> set;
        Toggle(String l, BooleanSupplier get, Consumer<Boolean> set) { super(l); this.get = get; this.set = set; }
        @Override String value() { return get.getAsBoolean() ? "on" : "off"; }
        @Override void click(int b, double fx) { set.accept(!get.getAsBoolean()); }
    }

    private static final class Cycle extends Entry {
        final Supplier<String> get; final Runnable next;
        Cycle(String l, Supplier<String> get, Runnable next) { super(l); this.get = get; this.next = next; }
        @Override String value() { return get.get(); }
        @Override void click(int b, double fx) { next.run(); }
    }

    private static final class Slider extends Entry {
        final double min, max, step; final DoubleSupplier get; final DoubleConsumer set; final String fmt;
        Slider(String l, double min, double max, double step, String fmt, DoubleSupplier get, DoubleConsumer set) {
            super(l); this.min = min; this.max = max; this.step = step; this.fmt = fmt; this.get = get; this.set = set;
        }
        @Override String value() { return String.format(fmt, get.getAsDouble()); }
        @Override double fill() { return (get.getAsDouble() - min) / (max - min); }
        @Override void click(int b, double fx) { drag(fx); }
        @Override void drag(double fx) {
            double v = min + Mth.clamp(fx, 0, 1) * (max - min);
            v = Math.round(v / step) * step;
            set.accept(Mth.clamp(v, min, max));
        }
    }

    public IntersticeConfigScreen(Screen parent) {
        super(Component.literal("The Interstice"));
        this.parent = parent;
        ClientConfig c = ClientConfig.INSTANCE;
        entries.add(new Header("GRAPHICS"));
        entries.add(new Cycle("Quality preset", () -> c.preset.name().toLowerCase(), () -> c.preset = c.preset.next()));
        entries.add(new Slider("Fog density", 0.4, 1.6, 0.05, "%.2f", () -> c.fogDensity, v -> c.fogDensity = (float) v));
        entries.add(new Toggle("Dynamic lighting", () -> c.dynamicLighting, v -> c.dynamicLighting = v));
        entries.add(new Toggle("Volumetric light", () -> c.volumetricLight, v -> c.volumetricLight = v));
        entries.add(new Toggle("Post-processing", () -> c.postProcessing, v -> c.postProcessing = v));
        entries.add(new Toggle("Bloom", () -> c.bloom, v -> c.bloom = v));
        entries.add(new Slider("Particles", 0, 2, 0.1, "%.1fx", () -> c.particles, v -> c.particles = (float) v));
        entries.add(new Cycle("Chain detail", () -> c.chainDetail < 0 ? "preset" : new String[]{"low", "medium", "high", "ultra"}[c.chainDetail],
                () -> c.chainDetail = c.chainDetail >= 3 ? -1 : c.chainDetail + 1));
        entries.add(new Header("COMFORT"));
        entries.add(new Slider("Camera shake", 0, 1.5, 0.05, "%.2f", () -> c.cameraShake, v -> c.cameraShake = (float) v));
        entries.add(new Toggle("Reduce flashing lights", () -> c.reduceFlashing, v -> c.reduceFlashing = v));
        entries.add(new Toggle("Reduce intense effects", () -> c.reduceIntenseEffects, v -> c.reduceIntenseEffects = v));
        entries.add(new Header("AUDIO"));
        entries.add(new Slider("Ambience", 0, 1.5, 0.05, "%.2f", () -> c.ambienceVolume, v -> c.ambienceVolume = (float) v));
        entries.add(new Slider("Voice", 0, 1.5, 0.05, "%.2f", () -> c.voiceVolume, v -> c.voiceVolume = (float) v));
        entries.add(new Toggle("Environmental audio (EFX)", () -> c.environmentalAudio, v -> c.environmentalAudio = v));
        entries.add(new Toggle("Voice subtitles", () -> c.voiceSubtitles, v -> c.voiceSubtitles = v));
        entries.add(new Header("DEVELOPMENT"));
        entries.add(new Toggle("Diagnostics overlay", () -> c.diagnostics, v -> c.diagnostics = v));
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            ServerConfig s = ServerConfig.INSTANCE;
            entries.add(new Slider("Event frequency", 0.25, 8, 0.25, "%.2fx", () -> s.eventFrequency, v -> s.eventFrequency = (float) v));
            entries.add(new Toggle("Horror director", () -> s.directorEnabled, v -> s.directorEnabled = v));
            entries.add(new Toggle("Natural Stampedes", () -> s.stampedeEnabled, v -> s.stampedeEnabled = v));
        }
    }

    private int panelW() { return Math.min(440, width - 32); }
    private int panelX() { return (width - panelW()) / 2; }
    private int top() { return 64; }

    private int contentHeight() {
        int h = 0;
        for (Entry e : entries) h += e.header() ? HEADER : ROW;
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        open = Math.min(1f, open + partialTick * 0.08f);
        float ease = 1f - (1f - open) * (1f - open);
        g.fillGradient(0, 0, width, height, (int) (0xD0 * ease) << 24 | 0x07090B, (int) (0xF0 * ease) << 24 | 0x030405);

        Font f = font;
        int px = panelX(), pw = panelW();
        int alpha = (int) (255 * ease);
        drawSpaced(g, "THE INTERSTICE", width / 2, 24, alpha << 24 | 0xD6D9DD, 3);
        String sub = "presentation and comfort";
        g.drawString(f, sub, (width - f.width(sub)) / 2, 38, (int) (alpha * 0.55f) << 24 | 0x9AA0A6, false);
        g.fill(px, 54, px + pw, 55, (int) (alpha * 0.25f) << 24 | 0xFFFFFF);

        int viewBottom = height - 36;
        double maxScroll = Math.max(0, contentHeight() - (viewBottom - top()));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(0, top() - 2, width, viewBottom);
        int y = top() - (int) scroll;
        for (Entry e : entries) {
            e.y = y;
            if (e.header()) {
                drawSpaced(g, e.label, px + 2, y + 10, alpha << 24 | 0x7E868E, 2, false);
                y += HEADER;
                continue;
            }
            boolean hovered = mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + ROW && mouseY < viewBottom;
            e.hover += ((hovered ? 1f : 0f) - e.hover) * 0.25f;
            if (e.hover > 0.01f) {
                g.fill(px, y, px + pw, y + ROW, (int) (e.hover * 0x18) << 24 | 0xFFFFFF);
                int lw = (int) (pw * e.hover);
                g.fill(px, y + ROW - 1, px + lw, y + ROW, (int) (e.hover * 0x90) << 24 | 0xC23B2E);
            }
            int labelCol = hovered ? 0xE6E8EA : 0xA9AFB5;
            g.drawString(f, e.label, px + 8, y + 5, alpha << 24 | labelCol, false);
            String v = e.value();
            int vw = f.width(v);
            double fill = e.fill();
            if (fill >= 0) {
                int bx = px + pw - 150, bw = 90;
                g.fill(bx, y + 9, bx + bw, y + 10, alpha / 3 << 24 | 0xFFFFFF);
                g.fill(bx, y + 8, bx + (int) (bw * fill), y + 11, alpha << 24 | 0xD0D3D6);
                g.drawString(f, v, px + pw - 8 - vw, y + 5, alpha << 24 | 0xF2F3F4, false);
            } else {
                g.drawString(f, v, px + pw - 8 - vw, y + 5, alpha << 24 | (v.equals("off") ? 0x6E747A : 0xF2F3F4), false);
            }
            y += ROW;
        }
        g.disableScissor();

        g.fill(px, height - 30, px + pw, height - 29, (int) (alpha * 0.25f) << 24 | 0xFFFFFF);
        String done = "done";
        int dw = f.width(done);
        boolean dh = mouseX >= width / 2 - 30 && mouseX < width / 2 + 30 && mouseY >= height - 24 && mouseY < height - 8;
        drawSpaced(g, done.toUpperCase(), width / 2, height - 20, alpha << 24 | (dh ? 0xFFFFFF : 0x9AA0A6), 2);
        String hint = "esc to close";
        g.drawString(f, hint, px + pw - f.width(hint), height - 20, (int) (alpha * 0.4f) << 24 | 0x9AA0A6, false);
    }

    private void drawSpaced(GuiGraphics g, String s, int x, int y, int color, int spacing) {
        drawSpaced(g, s, x, y, color, spacing, true);
    }

    private void drawSpaced(GuiGraphics g, String s, int x, int y, int color, int spacing, boolean centered) {
        int w = 0;
        for (char ch : s.toCharArray()) w += font.width(String.valueOf(ch)) + spacing;
        int cx = centered ? x - w / 2 : x;
        for (char ch : s.toCharArray()) {
            String c = String.valueOf(ch);
            g.drawString(font, c, cx, y, color, false);
            cx += font.width(c) + spacing;
        }
    }

    private Entry entryAt(double mx, double my) {
        int px = panelX(), pw = panelW();
        if (mx < px || mx >= px + pw || my < top() || my >= height - 36) return null;
        for (Entry e : entries) if (!e.header() && my >= e.y && my < e.y + ROW) return e;
        return null;
    }

    private double sliderFrac(double mx) {
        int bx = panelX() + panelW() - 150;
        return (mx - bx) / 90.0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my >= height - 24 && my < height - 8 && Math.abs(mx - width / 2.0) < 30) {
            onClose();
            return true;
        }
        Entry e = entryAt(mx, my);
        if (e != null) {
            if (e.fill() >= 0) {
                dragging = e;
                e.click(button, sliderFrac(mx));
            } else {
                e.click(button, 0);
            }
            playClick();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(sliderFrac(mx));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= sy * 18;
        return true;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.15f));
    }

    @Override
    public void onClose() {
        ClientConfig.save();
        if (Minecraft.getInstance().hasSingleplayerServer()) ServerConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the panel draws its own backdrop; the world stays visible behind it
    }
}
