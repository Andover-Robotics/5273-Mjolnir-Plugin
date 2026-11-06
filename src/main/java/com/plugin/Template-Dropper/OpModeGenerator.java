package com.plugin;

import java.util.ArrayList;
import java.util.List;

public class OpModeGenerator {

    private static String javaOpMode(String name, String opmode, String group, boolean teleop, boolean isLinear) {
        List<String> annotations = new ArrayList<>();
        if (!opmode.isEmpty()) annotations.add("name = \"" + opmode + "\"");
        if (!group.isEmpty()) annotations.add("group = \"" + group + "\"");
        String type = teleop ? "TeleOp" : "Autonomous";

        if (isLinear) {
            return String.join("\n",
                    "import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;",
                    "import com.qualcomm.robotcore.eventloop.opmode." + type + ";",
                    "",
                    "@" + type + "(" + String.join(", ", annotations) + ")",
                    "class " + name + " extends LinearOpMode {",
                    "    @Override",
                    "    public void runOpMode() {",
                    "        waitForStart();",
                    "        while (opModeIsActive()) {",
                    "            // fill in code",
                    "        }",
                    "    }",
                    "}"
            );
        }

        return String.join("\n",
                "import com.qualcomm.robotcore.eventloop.opmode.OpMode;",
                "import com.qualcomm.robotcore.eventloop.opmode." + type + ";",
                "",
                "@" + type + "(" + String.join(", ", annotations) + ")",
                "class " + name + " extends OpMode {",
                "    @Override",
                "    public void init() {",
                "        // put code here",
                "    }",
                "",
                "    @Override",
                "    public void loop() {",
                "        // put code here",
                "    }",
                "}"
        );
    }

    private static String kotlinOpMode(String name, String opmode, String group, boolean teleop, boolean isLinear) {
        List<String> annotations = new ArrayList<>();
        if (!opmode.isEmpty()) annotations.add("name = \"" + opmode + "\"");
        if (!group.isEmpty()) annotations.add("group = \"" + group + "\"");
        String type = teleop ? "TeleOp" : "Autonomous";

        if (isLinear) {
            return String.join("\n",
                    "import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode",
                    "import com.qualcomm.robotcore.eventloop.opmode." + type,
                    "",
                    "@" + type + "(" + String.join(", ", annotations) + ")",
                    "class " + name + ": LinearOpMode() {",
                    "    override fun runOpMode() {",
                    "        waitForStart()",
                    "        while (opModeIsActive()) {",
                    "            // fill in code",
                    "        }",
                    "    }",
                    "}"
            );
        }

        return String.join("\n",
                "import com.qualcomm.robotcore.eventloop.opmode.OpMode",
                "import com.qualcomm.robotcore.eventloop.opmode." + type,
                "",
                "@" + type + "(" + String.join(", ", annotations) + ")",
                "class " + name + ": OpMode() {",
                "    override fun init() {",
                "        // put code here",
                "    }",
                "",
                "    override fun loop() {",
                "        // put code here",
                "    }",
                "}"
        );
    }

    public static String createOpMode(boolean java, String name, String opmode, String group, boolean teleop, boolean isLinear) {
        return java ? javaOpMode(name, opmode, group, teleop, isLinear)
                : kotlinOpMode(name, opmode, group, teleop, isLinear);
    }
}
