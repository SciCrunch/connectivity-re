#!/bin/bash
printf -v var "%s," "$@"
gradle  runPMCCorpusBuilder -Ppargs="$var"
