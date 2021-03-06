/*
 * MIT License
 *
 * Copyright (c) 2017 Connor Hartley
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.connorhartley.guardian.sequence;

import io.github.connorhartley.guardian.Guardian;
import io.github.connorhartley.guardian.data.Keys;
import io.github.connorhartley.guardian.data.handler.SequenceHandlerData;
import io.github.connorhartley.guardian.detection.check.Check;
import io.github.connorhartley.guardian.detection.check.CheckController;
import io.github.connorhartley.guardian.detection.check.CheckProvider;
import io.github.connorhartley.guardian.event.sequence.SequenceBeginEvent;
import io.github.connorhartley.guardian.event.sequence.SequenceFinishEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.user.UserStorageService;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequence Controller
 *
 * Controls all the sequences for each {@link Check}.
 */
public class SequenceController implements SequenceInvoker {

    private final Object plugin;
    private final CheckController checkController;
    private final List<SequenceBlueprint> blueprints = new ArrayList<>();

    public SequenceController(Object plugin, CheckController checkController) {
        this.plugin = plugin;
        this.checkController = checkController;
    }

    @Override
    public void invoke(User user, Event event) {
        if (!user.get(Keys.GUARDIAN_SEQUENCE_HANDLER).isPresent()) user.offer((Sponge.getDataManager().getManipulatorBuilder(SequenceHandlerData.class).get()).create());

        user.get(Keys.GUARDIAN_SEQUENCE_HANDLER).ifPresent(sequences -> {
            sequences.forEach(sequence -> sequence.check(user, event));
            sequences.removeIf(Sequence::isCancelled);
            sequences.removeIf(Sequence::hasExpired);
            sequences.removeIf(sequence -> {
               if (!sequence.isFinished()) {
                   return false;
               }

               SequenceFinishEvent attempt = new SequenceFinishEvent(sequence, user, sequence.getSequenceResult().build(), Cause.of(NamedCause.source(this.plugin)));
               Sponge.getEventManager().post(attempt);

               if (attempt.isCancelled()) {
                   return true;
               }

               CheckProvider checkProvider = sequence.getProvider();
               this.checkController.post(checkProvider, sequence, user);
               return true;
            });

            this.blueprints.stream()
                    .filter(blueprint -> !sequences.contains(blueprint))
                    .forEach(blueprint -> {
                        Sequence sequence = blueprint.create(user);

                        SequenceBeginEvent attempt = new SequenceBeginEvent(sequence, user, sequence.getSequenceResult().build(), Cause.of(NamedCause.source(this.plugin)));
                        Sponge.getEventManager().post(attempt);

                        if (attempt.isCancelled()) {
                            return;
                        }

                        if (sequence.check(user, event)) {
                            if (sequence.isCancelled()) {
                                return;
                            }

                            if (sequence.isFinished()) {
                                CheckProvider checkProvider = sequence.getProvider();
                                this.checkController.post(checkProvider, sequence, user);
                                return;
                            }

                            sequences.add(sequence);
                            user.offer(((SequenceHandlerData.Builder) Sponge.getDataManager().getManipulatorBuilder(SequenceHandlerData.class).get()).createFrom(sequences));
                        }
                    });
        });
    }

    /**
     * Clean Up
     *
     * <p>Removes any {@link Sequence}s that have expired from the {@link User}s {@link SequenceHandlerData}.</p>
     */
    public void cleanup() {
        Sponge.getServer().getOnlinePlayers().forEach(player -> {
            Sponge.getServiceManager().provide(UserStorageService.class).ifPresent(userStorageService -> {
                userStorageService.get(player.getUniqueId()).ifPresent(user -> {
                    user.get(Keys.GUARDIAN_SEQUENCE_HANDLER).ifPresent(sequences -> sequences.removeIf(Sequence::hasExpired));
                });
            });
        });
    }

    /**
     * Force Clean Up
     *
     * <p>Removes the {@link User}'s data for {@link SequenceHandlerData}.</p>
     *
     * @param user {@link User} to remove data from
     */
    public void forceCleanup(User user) {
        user.remove(Keys.GUARDIAN_SEQUENCE_HANDLER);
    }

    /**
     * Force Clean Up
     *
     * <p>Removes data for {@link SequenceHandlerData} from all of the players online.</p>
     */
    public void forceCleanup() {
        Sponge.getServer().getOnlinePlayers().forEach(player -> {
            Sponge.getServiceManager().provide(UserStorageService.class).ifPresent(userStorageService -> {
                userStorageService.get(player.getUniqueId()).ifPresent(user -> user.remove(Keys.GUARDIAN_SEQUENCE_HANDLER));
            });
        });
    }

    /**
     * Register
     *
     * <p>Registers a {@link Sequence} from a {@link CheckProvider}.</p>
     *
     * @param checkProvider Provider of a {@link Sequence}
     */
    public void register(CheckProvider checkProvider) {
        this.blueprints.add(checkProvider.getSequence());
    }

    /**
     * Unregister
     *
     * <p>Unregisters a {@link Sequence} from a {@link CheckProvider}.</p>
     *
     * @param checkProvider
     */
    public void unregister(CheckProvider checkProvider) {
        this.blueprints.removeIf(blueprint -> blueprint.getCheckProvider().equals(checkProvider));
    }

    public static class SequenceControllerTask {

        private final Guardian plugin;
        private final SequenceController sequenceController;
        private final SequenceListener sequenceListener;

        private Task.Builder taskBuilder = Task.builder();
        private Task task;

        public SequenceControllerTask(Guardian plugin, SequenceController sequenceController) {
            this.plugin = plugin;
            this.sequenceController = sequenceController;
            this.sequenceListener = new SequenceListener(this.sequenceController);

            Sponge.getEventManager().registerListeners(this.plugin, this.sequenceListener);
        }

        public void start() {
            this.task = this.taskBuilder.execute(this.sequenceController::cleanup).intervalTicks(1)
                    .name("Guardian - Sequence Controller Task").submit(this.plugin);
        }

        public void stop() {
            if (this.task != null) this.task.cancel();

            Sponge.getEventManager().unregisterListeners(this.sequenceListener);
        }

    }

}
