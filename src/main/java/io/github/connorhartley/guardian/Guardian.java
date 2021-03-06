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
package io.github.connorhartley.guardian;

import com.google.inject.Inject;
import com.me4502.modularframework.ModuleController;
import com.me4502.modularframework.ShadedModularFramework;
import com.me4502.modularframework.exception.ModuleNotInstantiatedException;
import com.me4502.modularframework.module.ModuleWrapper;
import io.github.connorhartley.guardian.data.handler.SequenceHandlerData;
import io.github.connorhartley.guardian.data.tag.OffenseTagData;
import io.github.connorhartley.guardian.detection.Detection;
import io.github.connorhartley.guardian.detection.check.Check;
import io.github.connorhartley.guardian.detection.check.CheckController;
import io.github.connorhartley.guardian.sequence.Sequence;
import io.github.connorhartley.guardian.sequence.SequenceController;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;

import java.io.File;

@Plugin(
        id = "guardian",
        name = "Guardian",
        version = "6.0.0-0.1.0-01",
        description = "An extensible anticheat plugin for Sponge.",
        authors = {
                "Connor Hartley (vectrix)"
        }
)
public class Guardian {

    /* Logger */

    @Inject
    private Logger logger;

    public Logger getLogger() {
        return this.logger;
    }

    /* Plugin Instance */

    @Inject
    private PluginContainer pluginContainer;

    public PluginContainer getPluginContainer() {
        return this.pluginContainer;
    }

    /* Plugin Configuration */

    @Inject
    @DefaultConfig(sharedRoot = false)
    private File pluginConfig;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> pluginConfigManager;

    private ConfigurationOptions configurationOptions;

    public ConfigurationOptions getConfigurationOptions() {
        return this.configurationOptions;
    }

    private File pluginConfigDirectory = this.pluginConfig.getParentFile();

    /* Module System */

    private ModuleController moduleController;

    public ModuleController getModuleController() {
        return this.moduleController;
    }

    /* Configuration */

    private GuardianConfiguration globalConfiguration;

    /* Detections */

    private GuardianDetections internalDetections;

    /* Check / Sequence */

    private CheckController checkController;
    private SequenceController sequenceController;

    private CheckController.CheckControllerTask checkControllerTask;
    private SequenceController.SequenceControllerTask sequenceControllerTask;

    /* Game Events */

    @Listener
    public void onGameInitialize(GameInitializationEvent event) {
        getLogger().info("Starting Guardian AntiCheat.");

        Sponge.getDataManager().register(OffenseTagData.class, OffenseTagData.Immutable.class, new OffenseTagData.Builder());
        Sponge.getDataManager().register(SequenceHandlerData.class, SequenceHandlerData.Immutable.class, new SequenceHandlerData.Builder());
    }

    @Listener
    public void onServerStarting(GameStartingServerEvent event) {
        this.checkController = new CheckController(this);
        this.sequenceController = new SequenceController(this, this.checkController);

        this.checkControllerTask = new CheckController.CheckControllerTask(this, this.checkController);
        this.sequenceControllerTask = new SequenceController.SequenceControllerTask(this, this.sequenceController);

        getLogger().info("Loading global configuration.");

        this.globalConfiguration = new GuardianConfiguration(this, this.pluginConfig, this.pluginConfigManager);
        this.configurationOptions = ConfigurationOptions.defaults();
        this.globalConfiguration.load();

        getLogger().info("Discovering internal detections.");

        this.moduleController = ShadedModularFramework.registerModuleController(this, Sponge.getGame());
        this.moduleController.setConfigurationDirectory(this.pluginConfigDirectory);
        this.moduleController.setConfigurationOptions(this.configurationOptions);
        this.internalDetections = new GuardianDetections(this, this.moduleController);

        if (System.getProperty("TEST_GUARDIAN").contains("TRUE")) this.internalDetections
                .registerModule("io.github.connorhartley.guardian.module.DummyDetection");

        this.internalDetections.registerInternalModules();

        getLogger().info("Discovered " + this.moduleController.getModules().size() + " modules.");

        this.moduleController.enableModules(moduleWrapper -> {
            if (this.globalConfiguration.configEnabledDetections.getValue().contains(moduleWrapper.getId())) {
                getLogger().info("Enabled: " + moduleWrapper.getName() + " v" + moduleWrapper.getVersion());
                return true;
            }
            return false;
        });

        this.moduleController.getModules().stream()
                .filter(moduleWrapper -> !moduleWrapper.isEnabled())
                .forEach(moduleWrapper -> {
                    try {
                        if (moduleWrapper.getModule() instanceof Detection) {
                            Detection detection = (Detection) moduleWrapper.getModule();

                            detection.getChecks().forEach(check -> this.getSequenceController().register(check));
                        }
                    } catch(ModuleNotInstantiatedException e) {
                        getLogger().error("Failed to get module: " + moduleWrapper.getName() + " v" + moduleWrapper.getVersion());
                    }
                });

        this.globalConfiguration.update();

    }

    @Listener
    public void onServerStarted(GameStartedServerEvent event) {
        this.checkControllerTask.start();
        this.sequenceControllerTask.start();

        getLogger().info("Guardian AntiCheat is ready.");
    }

    @Listener
    public void onServerStopping(GameStoppingEvent event) {
        getLogger().info("Stopping Guardian AntiCheat.");

        this.sequenceControllerTask.stop();
        this.checkControllerTask.stop();

        this.sequenceController.forceCleanup();

        this.moduleController.getModules().stream()
                .filter(ModuleWrapper::isEnabled)
                .forEach(moduleWrapper -> {
                    try {
                        if (moduleWrapper.getModule() instanceof Detection) {
                            Detection detection = (Detection) moduleWrapper.getModule();

                            detection.getChecks().forEach(check -> this.getSequenceController().unregister(check));
                        }
                    } catch(ModuleNotInstantiatedException e) {
                        getLogger().error("Failed to get module: " + moduleWrapper.getName() + " v" + moduleWrapper.getVersion());
                    }
                });

        this.moduleController.disableModules(moduleWrapper -> {
            getLogger().info("Disabled: " + moduleWrapper.getName() + " v" + moduleWrapper.getVersion());
            return true;
        });

        getLogger().info("Stopped Guardian AntiCheat.");
    }

    @Listener
    public void onReload(GameReloadEvent event) {}

    /* Player Events */

    @Listener
    public void onClientDisconnect(ClientConnectionEvent.Disconnect event, @First User user) {
        this.sequenceController.forceCleanup(user);
    }

    /**
     * Get Global Configuration
     *
     * <p>Returns the configuration used by Guardian.</p>
     *
     * @return The guardian configuration
     */
    public GuardianConfiguration getGlobalConfiguration() {
        return this.globalConfiguration;
    }

    /**
     * Get Global Detections
     *
     * <p>Returns the built-in {@link Detection}s by Guardian.</p>
     *
     * @return The guardian built-in detections
     */
    public GuardianDetections getGlobalDetections() {
        return this.internalDetections;
    }

    /**
     * Get Sequence Controller
     *
     * <p>Returns the {@link SequenceController} for controlling the running of {@link Sequence}s for {@link Check}s.</p>
     *
     * @return The sequence controller
     */
    public SequenceController getSequenceController() {
        return this.sequenceController;
    }

}
