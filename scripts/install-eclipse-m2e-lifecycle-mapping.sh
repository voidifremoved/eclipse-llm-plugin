#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SOURCE_FILE="${REPO_ROOT}/releng/eclipse-lifecycle-mapping-metadata.xml"

usage() {
  cat <<EOF
Install m2e lifecycle mapping that prefers org.eclipse.m2e.pde.connector over
the legacy org.sonatype.tycho.m2e connector.

Usage:
  $0 <eclipse-workspace-directory>

Example:
  $0 ~/eclipse-workspace
EOF
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

WORKSPACE="$(cd "$1" && pwd)"
TARGET_DIR="${WORKSPACE}/.metadata/.plugins/org.eclipse.m2e.core"
TARGET_FILE="${TARGET_DIR}/lifecycle-mapping-metadata.xml"

if [[ ! -d "${WORKSPACE}/.metadata" ]]; then
  echo "Error: ${WORKSPACE} does not look like an Eclipse workspace (.metadata missing)" >&2
  exit 1
fi

if [[ ! -f "${SOURCE_FILE}" ]]; then
  echo "Error: source file not found: ${SOURCE_FILE}" >&2
  exit 1
fi

mkdir -p "${TARGET_DIR}"
cp "${SOURCE_FILE}" "${TARGET_FILE}"

echo "Installed lifecycle mapping to:"
echo "  ${TARGET_FILE}"
echo "Restart Eclipse, reload lifecycle mappings, then Maven -> Update Project."
