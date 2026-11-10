// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Content;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Control;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Text;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.dishanhai.shbui_kernel_example.ExampleUiModule.id;
import static com.dishanhai.shbui_kernel_example.ExampleUiModule.label;

/** Screen-owned producers. The kernel owns presentation, arbitration and action dispatch. */
final class LiveActivityExamples implements AutoCloseable {
    private static final UiId TOGGLE = id("live/toggle");
    private static final UiId CANCEL = id("live/cancel");
    private static final int[] NOTES = {0, 4, 7, 12, 7, 4, 2, 7, 11, 14, 11, 7, 5, 9, 7, 0};
    private static final int NOTE_TICKS = 8;
    private static final int TRACK_TICKS = NOTES.length * NOTE_TICKS;
    private static final int CRAFT_QUANTITY = 32;
    private static final int OPERATIONS = 16;
    private static final int ENERGY_PER_OPERATION = 30;
    private static final int BACKGROUND_TICKS = 500;
    private static final int FOREGROUND_START = 45;
    private static final int FOREGROUND_TICKS = 100;

    private final ModuleContext module;
    private final ScopeHandle scope;
    private final ActivityService activities;
    private final NotificationService notifications;
    private ActivityService.Handle music, crafting, machine, background, foreground;
    private SimpleSoundInstance note;
    private CraftingBackend.Job craftJob;
    private int playhead, machineTicks, energy, operations, priorityTicks;
    private boolean musicPaused, localCraft;

    LiveActivityExamples(ModuleContext module, ScopeHandle scope, ActivityService activities,
                         NotificationService notifications) {
        this.module = module;
        this.scope = scope;
        this.activities = activities;
        this.notifications = notifications;
    }

    String musicButton() {
        return music == null ? "music_start" : musicPaused ? "music_resume" : "music_pause";
    }

    String craftingButton() { return crafting == null ? "craft_start" : "craft_cancel"; }

    String machineButton() { return machine == null ? "machine_start" : "machine_cancel"; }

    void toggleMusic() {
        if (music == null) {
            playhead = 0;
            musicPaused = false;
            music = publish("music", musicContent(), Priority.NORMAL, action -> {
                if (action.id().equals(ActivityService.SEEK)) {
                    playhead = (int) Math.round(action.fraction() * (TRACK_TICKS - 1));
                    if (!musicPaused) playNote();
                    music.update(musicContent());
                } else if (action.id().equals(TOGGLE)) toggleMusic();
            });
        } else {
            musicPaused = !musicPaused;
            if (musicPaused) stopNote();
            else if (playhead % NOTE_TICKS != 0) playNote();
            music.update(musicContent());
        }
    }

    void toggleCrafting() {
        if (crafting != null) {
            cancelCraft();
            return;
        }
        try {
            var backend = ExampleUiModule.craftingBackend();
            localCraft = ExampleUiModule.localCraftingBackend(backend);
            craftJob = Objects.requireNonNull(backend.start(CRAFT_QUANTITY));
            crafting = publish("craft", content("craft_title", craftDetail(), "crafting", 0,
                    List.of(new Control(CANCEL, text("cancel")))), Priority.NORMAL, action -> cancelCraft());
            if (crafting == null) closeCraftJob();
        } catch (RuntimeException backendFailure) {
            closeCraftJob();
            notify("craft_error", "craft_error", "crafting");
        }
    }

    void toggleMachine() {
        if (machine != null) {
            cancel(machine);
            machine = null;
            return;
        }
        machineTicks = energy = operations = 0;
        machine = publish("machine", machineContent(), Priority.LOW, action -> toggleMachine());
    }

    void startPriority() {
        cancel(background);
        cancel(foreground);
        foreground = null;
        priorityTicks = 0;
        background = publish("background", content("priority_background", text("priority_wait"), "queue", 0,
                List.of()), Priority.LOW, null);
    }

    void tick() {
        if (music != null && !musicPaused) tickMusic();
        if (crafting != null) tickCrafting();
        if (machine != null) tickMachine();
        if (background != null) tickPriority();
    }

    private void tickMusic() {
        if (playhead % NOTE_TICKS == 0) playNote();
        if (++playhead >= TRACK_TICKS) {
            music.complete();
            music = null;
            stopNote();
        } else if (playhead % 2 == 0) music.update(musicContent());
    }

    private void tickCrafting() {
        try {
            int count = Math.max(0, Math.min(CRAFT_QUANTITY, craftJob.poll()));
            if (count == CRAFT_QUANTITY) {
                crafting.complete();
                crafting = null;
                String result = closeCraftJob() ? "craft_done" : "craft_error";
                notify(result, result, "crafting");
            } else crafting.update(content("craft_title", craftDetail(), "crafting", count / (double) CRAFT_QUANTITY,
                    List.of(new Control(CANCEL, text("cancel")))));
        } catch (RuntimeException backendFailure) {
            cancelCraft();
            notify("craft_error", "craft_error", "crafting");
        }
    }

    private void tickMachine() {
        machineTicks++;
        // Recharge every tick and consume energy only when the processing cycle can run.
        energy = Math.min(120, energy + 3);
        if (machineTicks % 14 == 0 && energy >= ENERGY_PER_OPERATION) {
            energy -= ENERGY_PER_OPERATION;
            if (++operations == OPERATIONS) {
                machine.complete();
                machine = null;
                notify("machine_done", "machine_done", "task");
                return;
            }
        }
        if (machineTicks % 4 == 0) machine.update(machineContent());
    }

    private void tickPriority() {
        priorityTicks++;
        if (priorityTicks == FOREGROUND_START) {
            foreground = publish("foreground", content("priority_foreground", text("priority_interrupt"), "task", 0,
                    List.of()), Priority.HIGH, null);
        }
        if (priorityTicks == FOREGROUND_START + FOREGROUND_TICKS && foreground != null) {
            foreground.complete();
            foreground = null;
        }
        if (priorityTicks >= BACKGROUND_TICKS) {
            background.complete();
            background = null;
        } else if (priorityTicks % 5 == 0) {
            background.update(content("priority_background", text("priority_wait"), "queue",
                    priorityTicks / (double) BACKGROUND_TICKS, List.of()));
            if (foreground != null) {
                foreground.update(content("priority_foreground", text("priority_interrupt"), "task",
                        (priorityTicks - FOREGROUND_START) / (double) FOREGROUND_TICKS, List.of()));
            }
        }
    }

    private Content musicContent() {
        float amplitude = musicPaused ? 0 : 1 - playhead % NOTE_TICKS / (float) NOTE_TICKS;
        return new Content(text("music_title"), text(musicPaused ? "music_paused" : "music_playing"), icon("music"),
                playhead / (double) TRACK_TICKS, true,
                List.of(new Control(TOGGLE, text(musicPaused ? "music_resume" : "music_pause"))),
                List.of(amplitude * .35f, amplitude * .6f, amplitude, amplitude * .75f, amplitude * .4f));
    }

    private void playNote() {
        stopNote();
        float pitch = (float) Math.pow(2, (NOTES[playhead / NOTE_TICKS] - 12) / 12d);
        note = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), pitch, .45f);
        Minecraft.getInstance().getSoundManager().play(note);
    }

    private void stopNote() {
        if (note == null) return;
        Minecraft.getInstance().getSoundManager().stop(note);
        note = null;
    }

    private Content machineContent() {
        Text detail = Text.literal(label("live.machine_energy", energy, operations, OPERATIONS).getString());
        return content("machine_title", detail, "task", operations / (double) OPERATIONS,
                List.of(new Control(CANCEL, text("cancel"))));
    }

    private Text craftDetail() { return text(localCraft ? "craft_local" : "craft_adapter"); }

    private ActivityService.Handle publish(String name, Content content, Priority priority, Consumer<ActivityService.Action> action) {
        try {
            return activities.publish(module, scope, new ActivityService.Request(id("live/" + name), content, priority,
                    Duration.ZERO, true, action));
        } catch (IllegalStateException unavailable) {
            notify("unavailable", "unavailable", "task");
            return null;
        }
    }

    private void notify(String name, String message, String icon) {
        notify(module, scope, notifications, name, message, icon);
    }

    static void notify(ModuleContext module, ScopeHandle scope, NotificationService notifications, String name,
                       String message, String icon) {
        try {
            UiId id = id("live/notice/" + name);
            notifications.publish(module, scope, new NotificationService.Request(id,
                    content(message, Text.literal(""), icon, -1, List.of()), Priority.NORMAL, Duration.ofSeconds(3),
                    id, NotificationService.Merge.COUNT, null));
        } catch (IllegalStateException unavailable) {
            // A user can disable notifications independently of the demo producer.
        }
    }

    static Text text(String key) { return Text.key(ExampleUiModule.MOD_ID + ".live." + key); }

    static UiId icon(String name) { return new UiId("sh_better_ui", "textures/gui/display/" + name + ".png"); }

    private static Content content(String title, Text detail, String icon, double progress, List<Control> controls) {
        return new Content(text(title), detail, icon(icon), progress, false, controls, List.of());
    }

    private void cancelCraft() {
        cancel(crafting);
        crafting = null;
        if (!closeCraftJob() && scope.isOpen()) notify("craft_error", "craft_error", "crafting");
    }

    private boolean closeCraftJob() {
        if (craftJob == null) return true;
        var job = craftJob;
        craftJob = null;
        try {
            job.close();
            return true;
        } catch (RuntimeException backendFailure) {
            return false;
        }
    }

    static void cancel(ActivityService.Handle handle) {
        if (handle != null && !handle.snapshot().lifecycle().terminal()) handle.cancel();
    }

    @Override public void close() {
        stopNote();
        cancel(music);
        cancel(machine);
        cancel(background);
        cancel(foreground);
        music = machine = background = foreground = null;
        cancelCraft();
        if (scope.isOpen()) notifications.dismissAll(module, scope);
    }
}
