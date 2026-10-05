#!/bin/bash
# Build the Linux packages, a .deb and a .tar.gz, and put them where tool/release.py takes them.
#
# Run on Linux, or in WSL from Windows, with a JDK 21 as JAVA_HOME:
#
#     JAVA_HOME=~/jdk21 bash tool/linux-packages.sh <libdivecomputer source>
#
# jpackage makes a package only for the system it runs on, so the Windows checkout cannot build
# these itself. The tree is copied to the Linux side and built there, because a build on WSL's view
# of a Windows drive is slow and would share its build folders with the Windows one. Then the two
# packages are copied back into ui/build/compose/binaries/main, beside the .msi. `LNX-1`.
#
# libdivecomputer is built first, by tool/libdivecomputer-linux.py, into the same Linux-side folder.
set -euo pipefail

if [ $# -ne 1 ]; then
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
source="$1"
repository="$(cd "$(dirname "$0")/.." && pwd)"
work="${YEMOJA_LINUX_WORK:-$HOME/.cache/yemoja-linux}"
library="$work/libdivecomputer"
tree="$work/tree"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "JAVA_HOME should name a JDK 21, and is not set"
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

python3 "$repository/tool/libdivecomputer-linux.py" "$source" "$library"

# The source alone: no history, no build results, and no local.properties, which names Windows
# paths.
mkdir -p "$tree"
(cd "$repository" && tar --exclude='./.git' --exclude='./build' --exclude='*/build' \
    --exclude='./.gradle' --exclude='*/.gradle' --exclude='./.kotlin' --exclude='./local.properties' \
    -cf - .) | (cd "$tree" && tar -xf -)
# A checkout on Windows may have written the wrapper with Windows line ends.
sed -i 's/\r$//' "$tree/gradlew"
chmod +x "$tree/gradlew"

built="$tree/ui/build/compose/binaries/main"
# An earlier version's packages would otherwise be copied back beside this one's.
rm -rf "${built:?}/deb" "${built:?}/tar"
(cd "$tree" && ./gradlew --no-daemon -q -Plibdivecomputer="$library" :ui:packageDeb :ui:packageTarGz --rerun)

into="$repository/ui/build/compose/binaries/main"
for kind in deb tar; do
    rm -rf "${into:?}/$kind"
    mkdir -p "$into/$kind"
    cp "$built/$kind"/* "$into/$kind/"
done
ls -l "$into/deb" "$into/tar"
