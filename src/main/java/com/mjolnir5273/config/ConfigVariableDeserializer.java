package com.mjolnir5273.config;

import com.google.gson.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ConfigVariableDeserializer implements JsonDeserializer<ConfigVariable<?>> {
    @Override
    public ConfigVariable<?> deserialize(
            JsonElement jsonElement,
            Type type,
            JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();
        VariableType varType =
                jsonDeserializationContext.deserialize(
                        obj.get(ConfigVariable.TYPE_KEY), VariableType.class);
        JsonElement valueEl = obj.get(ConfigVariable.VALUE_KEY);
        switch (varType) {
            case BOOLEAN:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(
                                valueEl.isJsonNull() ? null : valueEl.getAsBoolean()));
            case INT:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(valueEl.isJsonNull() ? null : valueEl.getAsInt()));
            case LONG:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(valueEl.isJsonNull() ? null : valueEl.getAsLong()));
            case FLOAT:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(valueEl.isJsonNull() ? null : valueEl.getAsFloat()));
            case DOUBLE:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(
                                valueEl.isJsonNull() ? null : valueEl.getAsDouble()));
            case STRING:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(
                                valueEl.isJsonNull() ? null : valueEl.getAsString()));
            case READONLY_STRING:
                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(
                                valueEl.isJsonNull() ? null : valueEl.getAsString()));
            case ENUM:
                if (valueEl.isJsonNull()) {
                    return new BasicVariable<>(varType, new ConstantProvider<>(null));
                }

                return new BasicVariable<>(
                        varType,
                        new ConstantProvider<>(
                                new EnumRep(obj.get(ConfigVariable.ENUM_CLASS_KEY).getAsString(),
                                        obj.get(ConfigVariable.VALUE_KEY).getAsString())));
            case CUSTOM:
                if (valueEl.isJsonNull()) {
                    return new CustomVariable(null);
                }

                CustomVariable customVariable = new CustomVariable();
                JsonObject valueObj = valueEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : valueObj.entrySet()) {
                    JsonObject childObj = entry.getValue().getAsJsonObject();
                    VariableType childType =
                            jsonDeserializationContext.deserialize(
                                    childObj.get(ConfigVariable.TYPE_KEY), VariableType.class);
                    ConfigVariable<?> child;
                    if (childType == VariableType.CUSTOM) {
                        child =
                                jsonDeserializationContext.deserialize(
                                        entry.getValue(), CustomVariable.class);
                    } else {
                        child =
                                jsonDeserializationContext.deserialize(
                                        entry.getValue(), BasicVariable.class);
                    }

                    customVariable.putVariable(entry.getKey(), child);
                }
                return customVariable;
            default:
                throw new RuntimeException();
        }
    }

    public static final class FTCDashboard {
        private final CompletableFuture<CustomVariable> result = new CompletableFuture<>();
        private @Nullable WebSocket socket;
        private static final Gson gson = new GsonBuilder()
                .registerTypeAdapter(Message.class, new MessageDeserializer())
                .registerTypeAdapter(BasicVariable.class, new ConfigVariableDeserializer())
                .registerTypeAdapter(CustomVariable.class, new ConfigVariableDeserializer())
                .serializeNulls()
                .create();
        private static CustomVariable parse(StringBuffer buffer) {
            try {
                Message msg = gson.fromJson(buffer.toString(), Message.class);
                if (msg.getType() != MessageType.RECEIVE_CONFIG) return null;
                return ((ReceiveConfig) msg).configRoot;
            } catch (JsonSyntaxException e) {
                return null;
            }
        }
        public enum Progress {
            SOCKET_OPEN,
        }
        public CompletableFuture<CustomVariable> getResult() {
            return result;
        }
        public void cancel() {
            if (socket != null) socket.abort();
        }
        public FTCDashboard(Consumer<Progress> progress) {
            HttpClient.newHttpClient().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .buildAsync(URI.create("ws://192.168.49.1:8000"), new WebSocket.Listener() {
                        private final StringBuffer current = new StringBuffer();

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            progress.accept(Progress.SOCKET_OPEN);
                            socket = webSocket;
                            webSocket.request(1);
                            webSocket.sendText("{ \"type\": \"GET_CONFIG\"}", true);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            current.append(data);
                            if (last) {
                                // Parse
                                CustomVariable out = parse(current);
                                current.setLength(0);
                                if (out != null) {
                                    cancel();
                                    result.complete(out);
                                }
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            cancel();
                            result.completeExceptionally(new Throwable(reason));
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            cancel();
                            result.completeExceptionally(error);
                        }
                    }).exceptionallyAsync(err -> {
                        cancel();
                        result.completeExceptionally(err);
                        return null;
                    });
        }
    }

    public static class FTCDashboardAction extends AnAction {
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
                    progressIndicator.setIndeterminate(false);
                    FTCDashboard dashboard = new FTCDashboard(p -> {
                        if (p == FTCDashboard.Progress.SOCKET_OPEN) {
                            progressIndicator.setFraction(0.333);
                            progressIndicator.setText2("Waiting for config");
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
                            progressIndicator.setText2("Indexing files");

                            // Do thing with config
                            performConfigUpdate(outVar, project, progressIndicator);
                        } catch (CompletionException e) {
                            onEDT(() -> Messages.showErrorDialog(e.getCause().getMessage(), "Failed to Sync"));
                        }
                    }
                }
            }.setCancelText("Cancel Sync").queue();
        }

        private static boolean isValidLiteral(PsiExpression expr) {
            if (expr instanceof PsiLiteralExpression) return true;
            if (expr instanceof PsiPrefixExpression e) {
                IElementType type = e.getOperationTokenType();
                return (type == JavaTokenType.MINUS || type == JavaTokenType.PLUS) && e.getOperand() instanceof PsiLiteralExpression;
            }
            return false;
        }

        private static class PsiWork {
            private final PsiField field;
            private final PsiExpression expr;

            PsiWork(PsiField f, PsiExpression e) {
                field = f;
                expr = e;
            }

            void perform() {
                field.setInitializer(expr);
            }
        }

        private static void performFieldUpdate(PsiField field, BasicVariable<?> var, ArrayList<PsiWork> work, PsiElementFactory factory)  {
            Object value = var.getValue();

            if (value == null) return;
            // Some static variables are complex expressions, so don't overwrite
            if (!isValidLiteral(field.getInitializer()) && var.getType() != VariableType.ENUM) return;

            String code;
            switch (var.getType()) {
                case INT:
                    if (!field.getType().equals(PsiTypes.intType())) return;
                    code = Integer.toString((int) value);
                    break;
                case ENUM:
                    if (field.getType() instanceof PsiPrimitiveType) return;
                    code = field.getType().getCanonicalText() + "." + ((EnumRep) value).value;
                    break;
                case STRING:
                case READONLY_STRING:
                    if (!field.getType().equalsToText("java.lang.String")) return;
                    code = '"' + ((String) value).replaceAll("\"", "\\\"") + '"';
                    break;
                case DOUBLE:
                    if (!field.getType().equals(PsiTypes.doubleType())) return;
                    code = Double.toString((double) value);
                    break;
                case BOOLEAN:
                    if (!field.getType().equals(PsiTypes.booleanType())) return;
                    code = Boolean.toString((boolean) value);
                    break;
                case LONG:
                    if (!field.getType().equals(PsiTypes.longType())) return;
                    code = Long.toString((long) value);
                    break;
                case FLOAT:
                    if (!field.getType().equals(PsiTypes.floatType())) return;
                    code = Float.toString((float) value);
                    break;
                case CUSTOM:
                    // Unimplemented
                default:
                    return;
            }

            // IntelliJ seems to mark files as changed even when the new contents are the same
            if (!code.equals(field.getInitializer().getText())) work.add(new PsiWork(field, factory.createExpressionFromText(code, null)));
        }

        private static void elementFinished(ArrayList<PsiWork> work, PsiClass[] classes, CustomVariable config, ProgressIndicator indicator, PsiElementFactory factory) {
            for (PsiClass out: classes) {
                if (out.isEnum() || out.isInterface()) continue;

                PsiAnnotation maybe = out.getAnnotation("com.acmerobotics.dashboard.config.Config");
                if (maybe != null && out.getAnnotation("com.com.qualcomm.robotcore.eventloop.opmode.Disabled") == null) {
                    PsiNameValuePair[] args = maybe.getParameterList().getAttributes();
                    String name = out.getName();
                    if (args.length > 0) {
                        PsiNameValuePair arg = args[0];
                        if (arg.getName() == null && arg.getValue() instanceof PsiLiteralExpression lit && lit.getValue() instanceof String newName) {
                            name = newName;
                        }
                    }
                    ConfigVariable<?> _toChange = config.variables.get(name);
                    if (_toChange instanceof CustomVariable toChange) {
                        for (PsiField field : out.getFields()) {
                            indicator.checkCanceled();

                            String fieldName = field.getName();
                            ConfigVariable<?> fieldValue = toChange.variables.get(fieldName);
                            if (fieldValue == null) continue;

                            if (fieldValue instanceof BasicVariable<?> var) {
                                if (!field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL))
                                    continue;
                                performFieldUpdate(field, var, work, factory);
                            } else {
                                CustomVariable var = (CustomVariable) fieldValue;

                                // This case can't really be solved in general, because the instance value may be nowhere in the source
                                // However, Roadrunner seems to like instantiating a class with purely public values, so we can just check for that
                                // and modify that class' source
                                if (field.getType() instanceof PsiPrimitiveType) continue;
                                PsiExpression init = field.getInitializer();
                                if (!(init instanceof PsiNewExpression ninit)) continue;
                                PsiJavaCodeReferenceElement clsRef = ninit.getClassOrAnonymousClassReference();
                                if (clsRef == null) continue;
                                if (!(clsRef.resolve() instanceof PsiClass cls)) continue;
                                for (PsiField ofield: cls.getFields()) {
                                    indicator.checkCanceled();

                                    ConfigVariable<?> innerValue = var.variables.get(ofield.getName());
                                    // We could handle CustomVariables by just making this recursive but there is really no point
                                    if (!ofield.hasModifierProperty(PsiModifier.PUBLIC) || !(innerValue instanceof BasicVariable<?> trueInnerValue)) continue;
                                    performFieldUpdate(ofield, trueInnerValue, work, factory);
                                }
                            }
                        }
                    }
                }
                elementFinished(work, out.getInnerClasses(), config, indicator, factory);
            }
        }

        private static void performConfigUpdate(CustomVariable config, Project project, ProgressIndicator progressIndicator) {
            PsiElementFactory factory = PsiElementFactory.getInstance(project);
            Collection<VirtualFile> files = ReadAction.compute(() -> FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project)));
            PsiManager manager = PsiManager.getInstance(project);
            ArrayList<PsiWork> work = new ArrayList<>();

            int done = 0;
            for (VirtualFile file: files) {
                progressIndicator.checkCanceled();
                ReadAction.run(() -> {
                    PsiJavaFile psiFile = (PsiJavaFile) manager.findFile(file);
                    if (psiFile == null) return;
                    PsiClass[] classes = psiFile.getClasses();
                    elementFinished(work, classes, config, progressIndicator, factory);
                });
                done++;
                progressIndicator.setText2("Scanning config (" + done + "/" + files.size() + ")");
                progressIndicator.setFraction(0.666 + 0.167 * done / files.size());
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                int done2 = 0;
                for (PsiWork w: work) {
                    progressIndicator.checkCanceled();
                    w.perform();
                    done2++;
                    progressIndicator.setText2("Applying config (" + done2 + "/" + work.size() + ")");
                    progressIndicator.setFraction(0.833 + 0.167 * done2 / work.size());
                }
            });
        }
    }
}