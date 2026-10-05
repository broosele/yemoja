"""Publish a release on GitHub, with the Windows installer, the Linux packages and the Android app.

    python tool/release.py <notes file>

The version is the build's own, `release` in gradle.properties, and so are the files' names; nothing
is typed twice. Build them first, the Linux two from a Linux shell or WSL:

    ./gradlew :ui:packageMsi :android:assembleRelease
    JAVA_HOME=~/jdk21 bash tool/linux-packages.sh <libdivecomputer source>

The repository's releases are immutable: nothing can be attached to one once it is published, no
attached file renamed, and a tag a deleted release used cannot be used again. So the release is
made as a draft, every file attached and their names checked, and only then published, as the
latest. The tag is the bare version and points at what is pushed: the commit here must be on
GitHub already.

The token is the one Git Credential Manager holds for github.com, and is never printed.
"""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "broosele/yemoja"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def version():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as held:
        return re.search(r"^release=(\S+)$", held.read(), re.M).group(1)


def token():
    asked = subprocess.run(
        ["git", "credential", "fill"], input=b"protocol=https\nhost=github.com\n\n",
        capture_output=True, env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
    )
    for line in asked.stdout.decode().splitlines():
        if line.startswith("password="):
            return line[len("password="):]
    sys.exit("no GitHub credential is held for github.com")


def call(method, url, body=None, data=None, kind="application/json"):
    if body is not None:
        data = json.dumps(body).encode()
    request = urllib.request.Request(url, method=method, data=data)
    request.add_header("Authorization", "Bearer " + TOKEN)
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("Content-Type", kind)
    try:
        with urllib.request.urlopen(request) as answer:
            return json.loads(answer.read() or b"{}")
    except urllib.error.HTTPError as failed:
        sys.exit(f"{method} {url.split('/repos/')[-1]}: {failed.code} {failed.read().decode()[:400]}")


def main(notes_file):
    tag = version()
    files = [
        (os.path.join(ROOT, "ui", "build", "compose", "binaries", "main", "msi", f"Yemoja-{tag}.msi"),
         f"Yemoja-{tag}.msi", "application/octet-stream"),
        (os.path.join(ROOT, "android", "build", "outputs", "apk", "release", "android-release.apk"),
         f"Yemoja-{tag}.apk", "application/vnd.android.package-archive"),
        (os.path.join(ROOT, "ui", "build", "compose", "binaries", "main", "deb", f"yemoja_{tag}_amd64.deb"),
         f"yemoja_{tag}_amd64.deb", "application/vnd.debian.binary-package"),
        (os.path.join(ROOT, "ui", "build", "compose", "binaries", "main", "tar", f"Yemoja-{tag}.tar.gz"),
         f"Yemoja-{tag}.tar.gz", "application/gzip"),
    ]
    for path, _, _ in files:
        if not os.path.isfile(path):
            sys.exit(f"{path} is not there: build it first")
    head = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, cwd=ROOT).stdout.decode().strip()
    pushed = subprocess.run(["git", "branch", "-r", "--contains", head], capture_output=True, cwd=ROOT).stdout.decode()
    if "origin/main" not in pushed:
        sys.exit(f"{head[:7]} is not pushed: push it first")
    with open(notes_file, encoding="utf-8") as held:
        notes = held.read()
    draft = call("POST", f"https://api.github.com/repos/{REPO}/releases", {
        "tag_name": tag, "target_commitish": head, "name": f"Yemoja {tag}", "body": notes,
        "draft": True, "prerelease": False,
    })
    for path, name, kind in files:
        with open(path, "rb") as held:
            data = held.read()
        asset = call("POST", f"https://uploads.github.com/repos/{REPO}/releases/{draft['id']}/assets?name={name}",
                     data=data, kind=kind)
        print("attached", asset["name"], asset["size"])
    attached = sorted(asset["name"] for asset in call("GET", draft["url"])["assets"])
    wanted = sorted(name for _, name, _ in files)
    if attached != wanted:
        sys.exit(f"the draft holds {attached}, not {wanted}: left as a draft, fix it on GitHub")
    published = call("PATCH", draft["url"], {"draft": False, "make_latest": "true"})
    print("published", published["html_url"])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    TOKEN = token()
    main(sys.argv[1])
