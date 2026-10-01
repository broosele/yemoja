"""Build libdivecomputer for Android, as one shared library per processor the app runs on.

The library's own build is autotools, which does not cross-compile from Windows without a POSIX
environment set up for it. Its sources are plain C with one configuration header, so they are
compiled here directly with the NDK's compiler, and the header is written for Android: no USB, no
IrDA and no classic Bluetooth, whose sources then build as the library's own "unsupported" stubs.
Android reads Bluetooth LE through the application, which hands the library a stream. `AND-6`.

The result is a separate file the app loads, as on the desktop, which is what the library's LGPL
asks of an application that is not itself LGPL. `LOGIC-27`.

    python tool/libdivecomputer-android.py <libdivecomputer source> <ndk> <out>

writes <out>/arm64-v8a/libdivecomputer.so and <out>/x86_64/libdivecomputer.so. Tell the build where
with -Plibdivecomputer.android=<out>, or the property of that name in ~/.gradle/gradle.properties.
"""

import os
import re
import subprocess
import sys
import tempfile

# The processors built for: a phone's, and the emulator's.
TARGETS = {"arm64-v8a": "aarch64-linux-android", "x86_64": "x86_64-linux-android"}

# The oldest Android the app runs on, `AND-2`, which the compiler links against.
API = 31

CONFIG = """\
/* Written for Android by tool/libdivecomputer-android.py. */
#define ENABLE_LOGGING 1
#define HAVE_CLOCK_GETTIME 1
#define HAVE_GMTIME_R 1
#define HAVE_LOCALTIME_R 1
#define HAVE_TIMEGM 1
#define HAVE_STRUCT_TM_TM_GMTOFF 1
#define HAVE_PTHREAD_H 1
#define HAVE_STRERROR_R 1
#define HAVE_LINUX_SERIAL_H 1
#define HAVE_UNISTD_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STDLIB_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_PARAM_H 1
#define STDC_HEADERS 1
#define PACKAGE "libdivecomputer"
#define VERSION "{version}"
"""


def sources(source):
    """The library's C sources, as its own build lists them, with POSIX serial for Windows'."""
    with open(os.path.join(source, "src", "Makefile.am"), encoding="utf-8") as made:
        text = made.read()
    listed = text.split("libdivecomputer_la_SOURCES =", 1)[1].split("\n\n", 1)[0]
    files = [name for name in re.findall(r"[\w-]+\.c", listed)]
    return files + ["serial_posix.c"]


def version(source):
    with open(os.path.join(source, "include", "libdivecomputer", "version.h"), encoding="utf-8") as held:
        return re.search(r'#define DC_VERSION "([^"]+)"', held.read()).group(1)


def compiler(ndk):
    host = "windows-x86_64" if os.name == "nt" else ("darwin-x86_64" if sys.platform == "darwin" else "linux-x86_64")
    name = "clang.exe" if os.name == "nt" else "clang"
    return os.path.join(ndk, "toolchains", "llvm", "prebuilt", host, "bin", name)


def build(source, ndk, out):
    clang = compiler(ndk)
    if not os.path.isfile(clang):
        sys.exit(f"{clang} is not there: is {ndk} an NDK?")
    configured = tempfile.mkdtemp(prefix="dc-android-")
    with open(os.path.join(configured, "config.h"), "w", encoding="utf-8") as config:
        config.write(CONFIG.replace("{version}", version(source)))
    files = sources(source)
    for abi, triple in TARGETS.items():
        objects = os.path.join(configured, abi)
        os.makedirs(objects, exist_ok=True)
        built = []
        for name in files:
            target = os.path.join(objects, name.replace(".c", ".o"))
            subprocess.run([
                clang, f"--target={triple}{API}", "-fPIC", "-O2", "-DHAVE_CONFIG_H",
                "-I", configured,
                "-I", os.path.join(source, "include"),
                "-I", os.path.join(source, "src"),
                "-c", os.path.join(source, "src", name), "-o", target,
                "-w",
            ], check=True)
            built.append(target)
        library = os.path.join(out, abi, "libdivecomputer.so")
        os.makedirs(os.path.dirname(library), exist_ok=True)
        subprocess.run([
            clang, f"--target={triple}{API}", "-shared", "-o", library,
            "-Wl,-soname,libdivecomputer.so", *built, "-lm", "-llog",
        ], check=True)
        print(f"{library}: {os.path.getsize(library)} bytes from {len(built)} sources")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    build(*sys.argv[1:])
