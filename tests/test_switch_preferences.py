from pathlib import Path
import os
import subprocess
import tempfile

work = Path(__file__).parent
root = work.parent
out = Path(tempfile.mkdtemp(prefix="spec-switch-"))
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
jars = []
for module in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime"):
    jars.extend((cache / "org.jetbrains.kotlin" / module / "1.9.24").glob("**/*.jar"))
for group, module in (("org.jetbrains.kotlin", "kotlin-reflect"),
                      ("org.jetbrains.intellij.deps", "trove4j"), ("org.jetbrains", "annotations")):
    jars.extend((cache / group / module).glob("**/*.jar"))
cp = os.pathsep.join(map(str, jars))
java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
sources = [work / p for p in ("StubContext.kt", "StubGraphics.kt", "StubView.kt", "StubAnimator.kt", "PreferencesDeepTest.kt")]
sources += [root / "app/src/main/java/com/legs/designqa" / p for p in ("AnnotationOptions.kt", "AnnotationPreferences.kt", "IosSwitch.kt", "HomeStyle.kt")]
subprocess.run([java, "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-nowarn",
                "-no-stdlib", "-no-reflect", "-classpath", cp, "-d", str(out),
                *map(str, sources)], check=True)
subprocess.run([java, "-cp", str(out) + os.pathsep + cp,
                "com.legs.designqa.PreferencesDeepTestKt"], check=True)
