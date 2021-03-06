package org.jenkinsci.plugins.spoontrigger.commands;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public final class CommandDriver {

    private static final int NO_ERROR = 0;

    private EnvVars env;
    private FilePath pwd;
    private TaskListener listener;
    private Launcher launcher;
    private boolean ignoreErrorCode = false;

    @Getter(AccessLevel.PACKAGE)
    private Charset charset;

    public static DriverBuilder builder() {
        return new DriverBuilder();
    }

    public static DriverBuilder builder(SpoonBuild build) {
        return new DriverBuilder()
                .charset(build.getCharset())
                .env(build.getEnv().get())
                .pwd(build.getWorkspace());
    }

    /**
     * Used to create DriverBuilder for building images using scripts
     */
    public static DriverBuilder scriptBuilder(SpoonBuild build) {
        checkArgument(build.getScript().isPresent(), "script is not defined");

        return new DriverBuilder()
                .charset(build.getCharset())
                .env(build.getEnv().get())
                .pwd(build.getScript().get().getParent());
    }

    int launch(ArgumentListBuilder argumentList) throws IllegalStateException {
        return this.launch(argumentList, this.getLogger());
    }

    int launch(ArgumentListBuilder argumentList, OutputStream out) throws IllegalStateException {
        int errorCode;
        try {
            errorCode = this.createLauncher().cmds(argumentList).stdout(out).join();
        } catch (IOException ex) {
            throw onLaunchFailure(argumentList, ex);
        } catch (InterruptedException ex) {
            throw onLaunchFailure(argumentList, ex);
        }

        if (!ignoreErrorCode && errorCode != NO_ERROR) {
            String errMsg = String.format("Process returned error code %d", errorCode);
            throw new IllegalStateException(errMsg);
        }

        return errorCode;
    }

    PrintStream getLogger() {
        return this.listener.getLogger();
    }

    private IllegalStateException onLaunchFailure(ArgumentListBuilder args, Exception ex) {
        String errMsg = String.format("Execution of command (%s) failed", args);
        return new IllegalStateException(errMsg, ex);
    }

    private Launcher.ProcStarter createLauncher() {
        return this.launcher.launch().pwd(this.pwd).envs(this.env);
    }

    public static class DriverBuilder {

        private final CommandDriver client;

        DriverBuilder() {
            this.client = new CommandDriver();
        }

        public DriverBuilder env(EnvVars environment) {
            this.client.env = environment;
            return this;
        }

        public DriverBuilder pwd(FilePath filePath) {
            this.client.pwd = filePath;
            return this;
        }

        public DriverBuilder listener(TaskListener listener) {
            this.client.listener = listener;
            return this;
        }

        public DriverBuilder launcher(Launcher launcher) {
            this.client.launcher = launcher;
            return this;
        }

        public DriverBuilder charset(Charset charset) {
            this.client.charset = charset;
            return this;
        }

        public DriverBuilder ignoreErrorCode(boolean ignoreErrorCode) {
            this.client.ignoreErrorCode = ignoreErrorCode;
            return this;
        }

        public CommandDriver build() {
            checkState(this.client.env != null, REQUIRE_PRESENT_S, "env");
            checkState(this.client.pwd != null, REQUIRE_PRESENT_S, "pwd");
            checkState(this.client.launcher != null, REQUIRE_PRESENT_S, "launcher");
            checkState(this.client.listener != null, REQUIRE_PRESENT_S, "listener");

            if (this.client.charset == null) {
                this.client.charset = Charset.defaultCharset();
            }

            return this.client;
        }
    }
}
