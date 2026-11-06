package com.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO Using modals to show errors is discouraged; should editor hints or balloons be used instead?
public class AddGradleDependency extends AnAction {
    // Index of the closure block child within a statement node (abstracted cause im sigma)
    private static final int CLOSURE_BLOCK_CHILD_INDEX = 2;

    // File path constants (abstract magic strings into well named constants)
    private static final String ROOT_BUILD_GRADLE = "build.gradle";
    private static final String BUILD_COMMON_GRADLE = "build.common.gradle";
    private static final int MIN_SDK = 24;
    private static final int TARGET_SDK = 28;
    private static final String JAVA_VERSION_STR = "JavaVersion.VERSION_1_8";

    /**
     * @param id                 unique id
     * @param displayName        shown in UI
     * @param mavenUrl           repository URL to add (nullable)
     * @param dependencyLines    lines to add to dependencies block
     * @param zipUrl             optional zip URL to import from (nullable)
     * @param zipSourcePath      path inside zip to pull files from (nullable)
     * @param zipDestinationPath destination relative to project base (nullable)
     * @param manualSteps        user-facing manual next steps for this library (nullable)
     * @param ensureMavenCentral if true, automatically add mavenCentral() to root build.gradle
     * @param ensureBuildCommon  if true, automatically apply defaultConfig/compileOptions edits to build.common.gradle
     * @param removeArm64Abi     if true, remove "arm64-v8a" entries from abiFilters in build.common.gradle (CV)
     */
    // Small DTO to hold all metadata for a library so adding new libraries is a single change.
    private record LibraryDescriptor(String id, String displayName, String mavenUrl, List<String> dependencyLines,
                                     String zipUrl, String zipSourcePath, String zipDestinationPath,
                                     String manualSteps,
                                     boolean ensureMavenCentral, boolean ensureBuildCommon, boolean removeArm64Abi) {
        private LibraryDescriptor(String id, String displayName,
                                  String mavenUrl, List<String> dependencyLines,
                                  String zipUrl, String zipSourcePath, String zipDestinationPath,
                                  String manualSteps,
                                  boolean ensureMavenCentral, boolean ensureBuildCommon, boolean removeArm64Abi) {
            this.id = id;
            this.displayName = displayName;
            this.mavenUrl = mavenUrl;
            this.dependencyLines = dependencyLines == null ? Collections.emptyList() : dependencyLines;
            this.zipUrl = zipUrl;
            this.zipSourcePath = zipSourcePath;
            this.zipDestinationPath = zipDestinationPath;
            this.manualSteps = manualSteps;
            this.ensureMavenCentral = ensureMavenCentral;
            this.ensureBuildCommon = ensureBuildCommon;
            this.removeArm64Abi = removeArm64Abi;
        }
    }

    // Registry of libraries, rn its just roadrunner and pedro
    private static final List<LibraryDescriptor> LIBRARY_REGISTRY = List.of(
            new LibraryDescriptor(
                    "roadrunner",
                    "Roadrunner",
                    "https://maven.brott.dev/",
                    Arrays.asList(
                            "implementation \"com.acmerobotics.roadrunner:ftc:0.1.25\"",
                            "implementation \"com.acmerobotics.roadrunner:core:1.0.1\"",
                            "implementation \"com.acmerobotics.roadrunner:actions:1.0.1\"",
                            "implementation \"com.acmerobotics.dashboard:dashboard:0.5.1\""
                    ),
                    // zip import info (optional) - keep existing quickstart sha URL
                    "https://github.com/acmerobotics/road-runner-quickstart/archive/6e63a7792e9bb6958798bf46fc03d84765b50c51.zip",
                    "TeamCode/src/main/java/org/firstinspires/ftc/teamcode",
                    "TeamCode/src/main/java/org/firstinspires/ftc/teamcode",
                    // manual steps shown to user after operation
                    "1) Perform a Gradle sync (press 'Sync Now' in the blue banner).\n" +
                            "2) If the import task added any resources, verify they appear correctly in your project.",
                    false, false, false
            ),
            new LibraryDescriptor(
                    "pedro",
                    "PedroPathing",
                    "https://mymaven.bylazar.com/releases",
                    Arrays.asList(
                            "implementation 'com.pedropathing:ftc:2.0.4'",
                            "implementation 'com.pedropathing:telemetry:1.0.0'",
                            "implementation 'com.bylazar:fullpanels:1.0.6'"
                    ),
                    // No zip import for Pedro in the official instructions, keep null
                    "https://github.com/Pedro-Pathing/Quickstart/archive/refs/tags/v2.0.4.zip",
                    "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/pedroPathing",
                    "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/pedroPathing",
                    """
                            1) Perform a Gradle sync (press 'Sync Now' in the blue banner).
                            2) Go to File > Project Structure > Modules and set Compile Sdk Version to 34 for FtcRobotController and TeamCode.
                            3) Press Apply and OK.""",
                    false, false, false
            ),
            // FTCLib core: automatically add mavenCentral and apply build.common.gradle edits
            new LibraryDescriptor(
                    "ftclib-core",
                    "FTCLib (core)",
                    "https://repo.maven.apache.org/maven2",
                    Arrays.asList(
                            "implementation 'org.ftclib.ftclib:core:2.1.1' // core"
                    ),
                    /* no zip import */ null, null, null,
                    // manual steps: instruct changes to root and build.common.gradle
                    "1) If anything failed, perform a Gradle sync (press 'Sync Now' in the blue banner).",
                    true, true, false
            ),
            // FTCLib vision: same as core plus CV-specific abi edits and guidance about OpenCV .so
            new LibraryDescriptor(
                    "ftclib-vision",
                    "FTCLib (vision)",
                    "https://repo.maven.apache.org/maven2",
                    Arrays.asList(
                            "implementation 'org.ftclib.ftclib:vision:2.1.0' // vision",
                            "implementation 'org.ftclib.ftclib:core:2.1.1' // core"
                    ),
                    /* no zip import */ null, null, null,
                    "1) FTCLib vision depends on EasyOpenCV which depends on OpenCV-Repackaged. Copy libOpenCvAndroid453.so (armeabi-v7a) into the FIRST folder on the Robot Controller (use MTP mode).\n" +
                            "2) If anything failed, perform a Gradle sync (press 'Sync Now' in the blue banner).",
                    true, true, true
            ),
            new LibraryDescriptor(
                    "nextftc",
                    "NextFTC",
                    null, // No additional Maven repository required
                    Arrays.asList(
                            "implementation 'dev.nextftc:ftc:1.0.1'",
                            "implementation 'dev.nextftc:hardware:1.0.1' // If you would like to use the hardware module"
                    ),
                    /* no zip import */ null, null, null,
                    "1) Perform a Gradle sync (press 'Sync Now' in the blue banner).",
                    false, false, false
            )
    );

    //builds checkboxes for each library
    private static final class RegistryDialog extends DialogWrapper {
        private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();

        RegistryDialog() {
            super(true);
            setTitle("Add Libraries");
            init();
        }

        @Override
        public JPanel createCenterPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            for (LibraryDescriptor lib: LIBRARY_REGISTRY) {
                JCheckBox cb = new JCheckBox(lib.displayName);
                boxes.put(lib.id, cb);
                panel.add(cb);
            }
            return panel;
        }

        boolean isSelected(String id) {
            JCheckBox cb = boxes.get(id);
            return cb != null && cb.isSelected();
        }

        // expose the set of selected library ids
        Set<String> getSelectedIds() {
            Set<String> s = new LinkedHashSet<>();
            for (String id : boxes.keySet()) {
                if (isSelected(id)) s.add(id);
            }
            return s;
        }
    }

    private static PsiFile getGradleFile(Project project, String basePath) {
        String fullPath = Paths.get(basePath, "TeamCode/build.gradle").toString();

        String fileUrl = "file://" + fullPath.replace("\\", "/");

        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(fileUrl);

        if (file == null) {
            Messages.showErrorDialog(project, "File build.gradle not found at TeamCode/build.gradle", "Unable to Make Gradle Changes");
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            Messages.showErrorDialog(project, "Failed to load the Gradle file.", "Unable to Make Gradle Changes");
            return null;
        }
        return psiFile;
    }

    private @Nullable GroovyMethodCallReference getReference(ArrayList<GroovyMethodCallReference> refs, String name) {
        for (GroovyMethodCallReference ref : refs) {
            if (ref.getMethodName().equals(name)) return ref;
        }
        return null;
    }

    //Ensure root build.gradle has mavenCentral() inside a repositories block andinsert safely if not present.
    private static void ensureMavenCentralInRoot(Project project, String basePath) {
        String fileUrl = "file://" + Paths.get(basePath, ROOT_BUILD_GRADLE).toString().replace("\\", "/");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
        if (vf == null) {
            Messages.showWarningDialog(project, "Root build.gradle not found", "Could Not Add mavenCentral()");
            return;
        }
        try {
            String content = new String(vf.contentsToByteArray(), StandardCharsets.UTF_8);
            if (content.contains("mavenCentral()")) return; // already present

            // If there's a repositories block, insert mavenCentral() right inside it; otherwise prepend a new block
            Pattern repoPattern = Pattern.compile("repositories\\s*\\{", Pattern.MULTILINE);
            Matcher m = repoPattern.matcher(content);
            if (m.find()) {
                int insertPos = m.end();
                StringBuilder sb = new StringBuilder(content);
                sb.insert(insertPos, "\n    mavenCentral()");
                vf.setBinaryContent(sb.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                // no repositories block; prepend one at the top
                String newContent = "repositories {\n    mavenCentral()\n}\n\n" + content;
                vf.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            Messages.showErrorDialog(project, "Failed to update root build.gradle: " + ex.getMessage(), "Could not Update build.gradle");
        }
    }

    // a few co
    private static void ensureBuildCommonSettings(Project project, String basePath, boolean removeArm64) {
        String[] candidatePaths = {BUILD_COMMON_GRADLE, "gradle/" + BUILD_COMMON_GRADLE, "buildSrc/" + BUILD_COMMON_GRADLE};
        VirtualFile vf = null;
        for (String p : candidatePaths) {
            String fileUrl = "file://" + Paths.get(basePath, p).toString().replace("\\", "/");
            vf = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
            if (vf != null) break;
        }
        if (vf == null) {
            Messages.showWarningDialog(project, "build.common.gradle not found in expected locations", "Manual Edits May Be Required");
            return;
        }
        try {
            String content = new String(vf.contentsToByteArray(), StandardCharsets.UTF_8);

            // Set minSdkVersion
            content = content.replaceAll("minSdkVersion\\s+\\d+", "minSdkVersion " + MIN_SDK);
            // Set targetSdkVersion
            content = content.replaceAll("targetSdkVersion\\s+\\d+", "targetSdkVersion " + TARGET_SDK);

            // Ensure multiDexEnabled true inside defaultConfig; attempt to insert after defaultConfig { if missing
            if (!content.contains("multiDexEnabled true")) {
                Pattern defaultConfigPattern = Pattern.compile("defaultConfig\\s*\\{", Pattern.MULTILINE);
                Matcher m = defaultConfigPattern.matcher(content);
                if (m.find()) {
                    int insertPos = m.end();
                    StringBuilder sb = new StringBuilder(content);
                    sb.insert(insertPos, "\n        multiDexEnabled true");
                    content = sb.toString();
                } else {
                    // fallback: append a defaultConfig snippet at the top of the file
                    content = "defaultConfig {\n    applicationId 'com.qualcomm.ftcrobotcontroller'\n    minSdkVersion " + MIN_SDK + "\n    targetSdkVersion " + TARGET_SDK + "\n    multiDexEnabled true\n}\n\n" + content;
                }
            }

            // Ensure compileOptions block exists and uses JavaVersion.VERSION_1_8
            if (!content.contains("compileOptions")) {
                content += "\ncompileOptions {\n    sourceCompatibility " + JAVA_VERSION_STR + "\n    targetCompatibility " + JAVA_VERSION_STR + "\n}\n";
            } else {
                content = content.replaceAll("sourceCompatibility\\s+JavaVersion\\.VERSION_\\d+_\\d+", "sourceCompatibility " + JAVA_VERSION_STR);
                content = content.replaceAll("targetCompatibility\\s+JavaVersion\\.VERSION_\\d+_\\d+", "targetCompatibility " + JAVA_VERSION_STR);
            }

            // Optionally remove arm64-v8a occurrences and ensure armeabi-v7a exists
            if (removeArm64) {
                // conservative removals of explicit "arm64-v8a" tokens
                content = content.replaceAll(",?\\s*\"arm64-v8a\"\\s*,?", ",");
                content = content.replaceAll("\"arm64-v8a\"", "");
                if (!content.contains("armeabi-v7a")) {
                    // append a simple ndk block if none of the armeabi references exist
                    content += "\nndk {\n    abiFilters \"armeabi-v7a\"\n}\n";
                }
            }

            vf.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Messages.showErrorDialog(project, ex.getMessage(), "Failed to Update build.gradle");
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("Project is not available.", "Project Not Available");
            return;
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }
        PsiFile gradleFile = getGradleFile(project, basePath);
        if (gradleFile == null) {
            return;
        }

        RegistryDialog dialog = new RegistryDialog();
        boolean res = dialog.showAndGet();
        if (!res) return;

        GroovyFile file = (GroovyFile) gradleFile;
        Collection<GrMethodCallExpression> expr = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class);
        ArrayList<GroovyMethodCallReference> refs = new ArrayList<>();
        for (GrMethodCallExpression state : expr) {
            GroovyMethodCallReference ref = state.getCallReference();
            if (ref != null) refs.add(ref);
        }

        GroovyPsiElementFactory fac = GroovyPsiElementFactory.getInstance(e.getProject());

        // find or create dependencies block
        GroovyMethodCallReference ref = getReference(refs, "dependencies");
        GrClosableBlock dependenciesBlock;
        GrStatement dependenciesStatement;
        if (ref == null) {
            dependenciesStatement = fac.createStatementFromText("dependencies {\n}");
            dependenciesBlock = (GrClosableBlock) dependenciesStatement.getChildren()[CLOSURE_BLOCK_CHILD_INDEX];
        } else {
            dependenciesStatement = null;
            if (((GrMethodCall) ref.getElement()).getClosureArguments().length == 0) {
                Messages.showErrorDialog(project, "The dependencies block is malformed.", "Unable to Make Dependency Changes");
                return;
            }
            dependenciesBlock = ((GrMethodCall) ref.getElement()).getClosureArguments()[0];
        }

        // find or create repositories by maven blocks
        GroovyMethodCallReference ref2 = getReference(refs, "maven");
        GrClosableBlock mavenBlock; // we can add url statements to this maven closure
        GrStatement mavenStatement; // created if there was no maven block
        GrClosableBlock repositoriesBlock;
        GrStatement repositoriesStatement;

        if (ref2 == null) {
            GroovyMethodCallReference ref3 = getReference(refs, "repositories");
            if (ref3 == null) {
                repositoriesStatement = fac.createStatementFromText("repositories {\n}");
                repositoriesBlock = (GrClosableBlock) repositoriesStatement.getChildren()[CLOSURE_BLOCK_CHILD_INDEX];
            } else {
                repositoriesStatement = null;
                repositoriesBlock = ((GrMethodCall) ref3.getElement()).getClosureArguments()[0];
            }
            mavenStatement = fac.createStatementFromText("maven {\n}");
            mavenBlock = (GrClosableBlock) mavenStatement.getChildren()[CLOSURE_BLOCK_CHILD_INDEX];
        } else {
            repositoriesStatement = null;
            repositoriesBlock = null;
            mavenStatement = null;
            mavenBlock = ((GrMethodCall) ref2.getElement()).getClosureArguments()[0];
        }

        // schedule background imports for libraries that provide a zip import
        Set<String> selected = dialog.getSelectedIds();
        for (LibraryDescriptor lib : LIBRARY_REGISTRY) {
            if (!selected.contains(lib.id)) continue;
            if (lib.zipUrl != null && lib.zipSourcePath != null && lib.zipDestinationPath != null) {
                final String zip = lib.zipUrl;
                final String src = lib.zipSourcePath;
                final String dest = lib.zipDestinationPath;
                final String label = lib.displayName;
                // TODO use Kotlin so obsolete API is not used
                new Task.Backgroundable(project, "Importing " + label) {
                    @Override
                    public void run(@NotNull ProgressIndicator progressIndicator) {
                        progressIndicator.setIndeterminate(true);
                        GithubImports.importFromZip(basePath, zip, src, dest, progressIndicator);
                    }
                }.setCancelText("Cancel Import").queue();
            }
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            // For each selected library add maven URL and dependencies
            for (LibraryDescriptor lib : LIBRARY_REGISTRY) {
                if (!selected.contains(lib.id)) continue;

                // If requested, apply root/build.common.gradle edits to reduce manual steps
                if (lib.ensureMavenCentral) {
                    ensureMavenCentralInRoot(project, basePath);
                }
                if (lib.ensureBuildCommon) {
                    ensureBuildCommonSettings(project, basePath, lib.removeArm64Abi);
                }

                // add maven repo url if present (TeamCode build.gradle repositories/maven block)
                if (lib.mavenUrl != null && !lib.mavenUrl.isEmpty()) {
                    mavenBlock.addStatementBefore(fac.createStatementFromText("url = '" + lib.mavenUrl + "'"), null);
                }

                // add each dependency line
                for (String dep : lib.dependencyLines) {
                    dependenciesBlock.addStatementBefore(fac.createStatementFromText(dep), null);
                }
            }

            if (dependenciesStatement != null) file.addStatementBefore(dependenciesStatement, null);
            if (mavenStatement != null) repositoriesBlock.addStatementBefore(mavenStatement, null);
            if (repositoriesStatement != null) file.addStatementBefore(repositoriesStatement, null);
        });

        // Compose per-library next steps using descriptor.manualSteps if present
        StringBuilder nextSteps = new StringBuilder();
        for (LibraryDescriptor lib : LIBRARY_REGISTRY) {
            if (!selected.contains(lib.id)) continue;
            nextSteps.append(lib.displayName).append(" - Next steps (manual):\n");
            if (lib.manualSteps != null && !lib.manualSteps.isEmpty()) {
                nextSteps.append(lib.manualSteps).append("\n\n");
            } else {
                // default generic instruction
                nextSteps.append("1) Perform a Gradle sync (press 'Sync Now' in the blue banner).\n\n");
            }
        }
        if (nextSteps.isEmpty()) {
            nextSteps.append("No libraries were selected.");
        }

        Messages.showInfoMessage(project, nextSteps.toString().trim(), "Library Import - Manual Steps");
    }
}
