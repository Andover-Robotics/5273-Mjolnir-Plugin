package com.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.plugin.config.CustomVariable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class FTCDashboardAction extends AnAction {
    private static void onEDT(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        if (project == null) {
            Messages.showErrorDialog("Project is not available.", "Project Not Available");
            return;
        }
        new Task.Backgroundable(project, "Syncing FTC Dashboard") {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                progressIndicator.setText("Syncing dashboard");
                progressIndicator.setFraction(0);
                progressIndicator.setText2("Waiting for connection");
                FTCDashboard dashboard = new FTCDashboard(p -> {
                    switch (p) {
                        case SOCKET_OPEN -> {
                            progressIndicator.setFraction(0.333);
                            progressIndicator.setText2("Waiting for config");
                        }
                    }
                });
                try (ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor()) {
                    CompletableFuture<CustomVariable> canceller = new CompletableFuture<>();
                    // Java try to create a good asynchronous API challenge:
                    service.scheduleAtFixedRate(() -> {
                        if (progressIndicator.isCanceled()) {
                            dashboard.cancel();
                            canceller.complete(null);
                        }
                    }, 0, 50, TimeUnit.MILLISECONDS);
                    try {
                        CustomVariable outVar = (CustomVariable) CompletableFuture.anyOf(canceller, dashboard.getResult()).join();
                        if (outVar == null) return;

                        progressIndicator.setFraction(0.666);
                        progressIndicator.setText2("Applying config");

                        // Do thing with config
                    } catch (CompletionException e) {
                        onEDT(() -> {
                            Messages.showErrorDialog(e.getCause().getMessage(), "Failed to Sync");
                        });
                    }
                }
            }
        }.setCancelText("Cancel Sync").queue();
    }
}
