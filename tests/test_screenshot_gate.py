#!/usr/bin/env python3
"""Run the isolated screenshot scheduling regression against actual Kotlin code."""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
jars = []
for module in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime"):
    jars.extend((cache / "org.jetbrains.kotlin" / module / "1.9.24").glob("**/*.jar"))
for group, module in (("org.jetbrains.kotlin", "kotlin-reflect"),
                      ("org.jetbrains.intellij.deps", "trove4j"),
                      ("org.jetbrains", "annotations")):
    jars.extend((cache / group / module).glob("**/*.jar"))
java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
classpath = os.pathsep.join(map(str, jars))
with tempfile.TemporaryDirectory(prefix="spec-screenshot-test-") as output:
    subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", output,
                    str(root / "app/src/main/java/com/legs/designqa/ScreenshotGate.kt"),
                    str(root / "tests/ScreenshotGateRegression.kt")], check=True)
    subprocess.run([java, "-cp", output + os.pathsep + classpath,
                    "com.legs.designqa.ScreenshotGateRegressionKt"], check=True)
