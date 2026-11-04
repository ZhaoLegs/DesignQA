#!/usr/bin/env python3
"""Run actual connection status policy with no Android timing dependencies."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
jars = []
for group, module in (("org.jetbrains.kotlin", "kotlin-compiler-embeddable"),
                      ("org.jetbrains.kotlin", "kotlin-stdlib"),
                      ("org.jetbrains.kotlin", "kotlin-script-runtime"),
                      ("org.jetbrains.kotlin", "kotlin-reflect"),
                      ("org.jetbrains.intellij.deps", "trove4j"),
                      ("org.jetbrains", "annotations")):
    jars.extend((cache / group / module).glob("**/*.jar"))
java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
cp = os.pathsep.join(map(str, jars))
with tempfile.TemporaryDirectory(prefix="spec-connection-test-") as output:
    test = Path(output) / "ConnectionTest.kt"
    test.write_text("""
package com.legs.designqa
fun main() {
    check(connectionState(false, false, 0) == ConnectionState.DISABLED)
    check(connectionState(true, false, 0) == ConnectionState.CONNECTING)
    check(connectionState(true, false, 7999) == ConnectionState.CONNECTING)
    check(connectionState(true, false, 8000) == ConnectionState.INTERRUPTED)
    check(connectionState(true, true, 9000) == ConnectionState.CONNECTED)
    println("PASS: enabled service receives connection grace, live binding determines readiness")
}
""")
    subprocess.run([java, "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-nowarn", "-no-stdlib", "-no-reflect", "-classpath", cp, "-d", output,
                    str(ROOT / "app/src/main/java/com/legs/designqa/ConnectionState.kt"), str(test)], check=True)
    subprocess.run([java, "-cp", output + os.pathsep + cp,
                    "com.legs.designqa.ConnectionTestKt"], check=True)
