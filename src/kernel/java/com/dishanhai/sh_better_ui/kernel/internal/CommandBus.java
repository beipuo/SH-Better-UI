// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

final class CommandBus {
    private final CentralScheduler scheduler;
    private final StateStore states;
    private final DirtyWork dirty;
    private final MutationCoordinator mutations;
    private final Map<UiId, Binding<?>> handlers = new LinkedHashMap<>();

    CommandBus(CentralScheduler scheduler, StateStore states, DirtyWork dirty, MutationCoordinator mutations) {
        this.scheduler = scheduler;
        this.states = states;
        this.dirty = dirty;
        this.mutations = mutations;
    }

    UICommandBus view(ModuleAccess access) {
        return new UICommandBus() {
            @Override
            public <T> Subscription register(CommandKey<T> key, UIPermission required, UICommandHandler<T> handler) {
                scheduler.assertMutationPhase();
                access.assertSideEffectAllowed();
                access.requireSchemaOwner(key.id());
                var binding = new Binding<>(key, access.requireIssuer(), required, Objects.requireNonNull(handler));
                if (handlers.putIfAbsent(key.id(), binding) != null) {
                    throw new IllegalArgumentException("Duplicate UI command " + key.id());
                }
                return binding;
            }

            @Override
            public <T> CommandResult execute(UICommand<T> command) {
                return CommandBus.this.execute(access, command);
            }

            @Override
            public <T> CommandHandle submit(UICommand<T> command, long timeoutNanos) {
                var receipt = new Receipt();
                receipt.task = scheduler.submit(access, UITask.ui(command.target(), UIPhase.STATE_MUTATION,
                        UIPriority.NORMAL, () -> {
                            try {
                                receipt.result = execute(command);
                            } catch (RuntimeException | LinkageError | AssertionError failure) {
                                receipt.failure = failure;
                                throw failure;
                            }
                        }).withTimeout(timeoutNanos));
                return receipt;
            }

            @Override
            public CommandResult transaction(Consumer<UITransaction> prepare) {
                return CommandBus.this.transaction(access, prepare);
            }
        };
    }

    private <T> CommandResult execute(ModuleAccess access, UICommand<T> command) {
        return transaction(access, editor -> {
            var target = access.write(command.target());
            var binding = handlers.get(command.key().id());
            if (binding == null || !binding.isActive() || !binding.key.equals(command.key())) {
                throw new NoSuchElementException("No matching UI command handler for " + command.key().id());
            }
            if (binding.required != null) {
                access.require(binding.required, target);
            }
            binding.prepare(target, command.payload(), editor);
        });
    }

    private CommandResult transaction(ModuleAccess access, Consumer<UITransaction> prepare) {
        Objects.requireNonNull(prepare, "prepare");
        return mutations.write(() -> {
            var editor = new Editor(access, states.begin(access));
            try {
                try {
                    prepare.accept(editor);
                } finally {
                    editor.open = false;
                }
                var invalidations = new LinkedHashMap<ScopeLifecycle.Scope, Set<UIDirtyFlag>>();
                for (var entry : editor.invalidations.entrySet()) {
                    var target = dirty.validate(access, entry.getKey());
                    invalidations.merge(target, entry.getValue(), Editor::combine);
                }
                var state = editor.state.prepare();
                return scheduler.commitEffects(access, editor.effects, () -> {
                    long version = states.publish(state);
                    invalidations.forEach(dirty::invalidate);
                    return version;
                });
            } finally {
                editor.invalidations.clear();
                editor.effects.clear();
                editor.state.discard();
            }
        });
    }

    void remove(List<ScopeLifecycle.Scope> closing) {
        if (closing.stream().noneMatch(scope -> scope.kind() == ScopeKind.MODULE || scope.kind() == ScopeKind.ENGINE)) {
            return;
        }
        for (var binding : List.copyOf(handlers.values())) {
            if (!binding.owner.isOpen()) {
                binding.close();
            }
        }
    }

    private final class Binding<T> implements Subscription {
        private final CommandKey<T> key;
        private final ScopeLifecycle.Scope owner;
        private final UIPermission required;
        private volatile UICommandHandler<T> handler;

        private Binding(CommandKey<T> key, ScopeLifecycle.Scope owner, UIPermission required, UICommandHandler<T> handler) {
            this.key = key;
            this.owner = owner;
            this.required = required;
            this.handler = handler;
        }

        void prepare(ScopeLifecycle.Scope scope, Object payload, UITransaction editor) {
            handler.prepare(scope.reference(), key.type().cast(payload), editor);
        }

        @Override
        public boolean isActive() {
            return handler != null && owner.isOpen();
        }

        @Override
        public void close() {
            if (handler != null) {
                owner.assertOwner();
                mutations.assertOutsideValidation();
                handler = null;
                handlers.remove(key.id(), this);
            }
        }
    }

    private static final class Editor implements UITransaction {
        private final ModuleAccess access;
        private final StateStore.Transaction state;
        private final Map<ScopeHandle, Set<UIDirtyFlag>> invalidations = new LinkedHashMap<>();
        private final List<UITask> effects = new ArrayList<>();
        private boolean open = true;

        private Editor(ModuleAccess access, StateStore.Transaction state) {
            this.access = access;
            this.state = state;
        }

        @Override
        public <T> UITransaction set(ScopeHandle scope, StateKey<T> key, T value) {
            checkOpen();
            state.set(scope, key, value);
            return this;
        }

        @Override
        public <T> UITransaction update(ScopeHandle scope, StateKey<T> key, UnaryOperator<T> updater) {
            checkOpen();
            state.update(scope, key, updater);
            return this;
        }

        @Override
        public UITransaction invalidate(ScopeHandle node, Set<UIDirtyFlag> flags) {
            checkOpen();
            invalidations.merge(Objects.requireNonNull(node, "node"), Set.copyOf(flags), Editor::combine);
            return this;
        }

        private static Set<UIDirtyFlag> combine(Set<UIDirtyFlag> left, Set<UIDirtyFlag> right) {
            var union = java.util.EnumSet.noneOf(UIDirtyFlag.class);
            union.addAll(left);
            union.addAll(right);
            return union;
        }

        @Override
        public UITransaction afterCommit(UITask effect) {
            checkOpen();
            effects.add(Objects.requireNonNull(effect, "effect"));
            return this;
        }

        private void checkOpen() {
            access.requireIssuer().assertOwner();
            if (!open) {
                throw new IllegalStateException("UI command preparation has ended");
            }
        }
    }

    private static final class Receipt implements CommandHandle {
        private volatile TaskHandle task;
        private volatile CommandResult result;
        private volatile Throwable failure;

        @Override public TaskHandle.Status status() { return task.status(); }
        @Override public boolean cancel() { return task.cancel(); }
        @Override public Optional<CommandResult> result() { return Optional.ofNullable(result); }
        @Override public Optional<Throwable> failure() { return Optional.ofNullable(failure); }
    }
}
