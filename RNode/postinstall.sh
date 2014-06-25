#!/bin/sh
WORKPATH=$1
EXTPATH=$2
if [ -f "${WORKPATH}/common/profiles/RNode.sh" ]; then
	rm -f "${WORKPATH}/common/profiles/RNode.sh"
fi
cp -f "${EXTPATH}/samples/RNode.sh" "${WORKPATH}/common/profiles/RNode.sh"
