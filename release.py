#!/usr/bin/python

import sys
import os
import subprocess
import requests
from git import Repo

GITHUB_TOKEN = os.getenv("GITHUB_TRISKEL_TOKEN")
GITHUB_REPO = "triskellib/ghidra"


def check_clean_repo():
    repo = Repo(".")
    if repo.is_dirty(untracked_files=True):
        print("Git working directory is not clean. Commit or stash changes first.")
        sys.exit(1)


def create_git_tag(version):
    tag = f"v{version}"
    subprocess.run(["git", "tag", tag], check=True)
    subprocess.run(["git", "push", "origin", tag], check=True)
    return tag


def create_github_release(tag):
    url = f"https://api.github.com/repos/{GITHUB_REPO}/releases"
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json",
    }
    data = {
        "tag_name": tag,
        "name": tag,
        "body": f"Release {tag}",
        "draft": False,
        "prerelease": False,
    }
    response = requests.post(url, headers=headers, json=data)
    response.raise_for_status()
    return response.json()["upload_url"].split("{")[0]


def upload_assets(upload_url):
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Content-Type": "application/octet-stream",
    }

    for filename in os.listdir("dist"):
        file_path = os.path.join("dist", filename)
        if os.path.isfile(file_path):
            with open(file_path, "rb") as f:
                print(f"Uploading {filename}")
                response = requests.post(
                    f"{upload_url}?name={filename}", headers=headers, data=f
                )
                response.raise_for_status()


def build():
    subprocess.run(["rm", "-rf", "./build/*"], check=True)
    subprocess.run(["rm", "-rf", "./dist/*"], check=True)
    subprocess.run(["gradle", "--stop"], check=True)
    subprocess.run(["gradle", "distributeExtension"], check=True)


def main():
    if len(sys.argv) != 2:
        print("Usage: release.py <version>")
        sys.exit(1)

    build()

    version = sys.argv[1]
    check_clean_repo()
    tag = create_git_tag(version)
    print(f"Created and pushed tag {tag}")
    upload_url = create_github_release(tag)
    upload_assets(upload_url)
    print(f"Release {tag} created successfully!")


if __name__ == "__main__":
    main()
