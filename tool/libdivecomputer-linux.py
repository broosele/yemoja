"""Build libdivecomputer for Linux, as the shared library the desktop installation carries.

Run on Linux, or in WSL. The library's own build is autotools, and its source tree as prepared for
the Windows build already holds the generated configure script, so a copy of it builds here with
the compiler and make alone. The copy is made so the tree's Windows build is left as it is.

USB is built in through libusb and hidapi, as on Windows, and both are left to the system: they
are libraries a distribution carries, and the package depends on them rather than holding them.
Classic Bluetooth through BlueZ is left out. BlueZ's library is GPL, which the application does
not take on, and Bluetooth LE reaches the system's stack through Kable instead. `LOGIC-27`.

    python3 tool/libdivecomputer-linux.py <libdivecomputer source> <out>

writes <out>/libdivecomputer.so. Tell the build where with -Plibdivecomputer=<out>.
"""

import os
import shutil
import subprocess
import sys
import tempfile

# What a copy of the source leaves behind: the Windows build's own results.
LEFT_BEHIND = shutil.ignore_patterns(
    "*.o", "*.lo", "*.la", ".libs", ".deps", "*.dll", "*.exe", "autom4te.cache",
)


def main(source, out):
    with tempfile.TemporaryDirectory() as work:
        tree = os.path.join(work, "libdivecomputer")
        shutil.copytree(source, tree, ignore=LEFT_BEHIND)
        # The copied Makefiles and config.status are the Windows build's, so configure starts over.
        for stale in ("config.status", "config.log", "Makefile", "config.h", "libtool"):
            path = os.path.join(tree, stale)
            if os.path.exists(path):
                os.remove(path)
        run(["sh", "./configure", "--enable-shared", "--disable-static", "--without-bluez"], tree)
        run(["make", "-j", str(os.cpu_count() or 2)], tree)
        os.makedirs(out, exist_ok=True)
        built = os.path.realpath(os.path.join(tree, "src", ".libs", "libdivecomputer.so"))
        # One file under the name the application loads, rather than the chain of version links.
        shutil.copyfile(built, os.path.join(out, "libdivecomputer.so"))
    print("wrote " + os.path.join(out, "libdivecomputer.so"))


def run(command, where):
    subprocess.run(command, cwd=where, check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
