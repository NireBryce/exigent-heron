# Recipes work from anywhere in the repo.
#
# This file is an interface, not a home for logic. Anything with a conditional,
# a pipeline or a reason worth explaining belongs in scripts/ -- where it can be
# run directly, and where the explanation sits next to the code it explains
# rather than in a recipe body nobody reads. Recipes should stay one line of
# dispatch plus the one-line summary `just --list` shows. Same rule, and the
# same reasoning, as nixos-configs' own .justfile.
#
# Everything here needs the dev shell's PATH (gradle, adb, emulator): run these
# inside `nix develop`, or let direnv do it. scripts/test.sh says so by name
# rather than failing obscurely if a tool is missing.
#
# Note that `just --list` shows only the LAST comment line above a recipe, so
# each one gets a single-line summary and any detail goes in the script.

scripts := justfile_directory() / "scripts"

_default:
    @just --list

# Fast enough to run constantly; the only suite that needs no device
test:
    @{{scripts}}/test.sh unit

# Boots a headless emulator if none is attached, and stops only one it started
test-device:
    @{{scripts}}/test.sh device

# androidTest only -- DataStore round-trips, real TextToSpeech, mirrored constants
test-instrumented:
    @{{scripts}}/test.sh instrumented

# Posts real notifications and asserts on decision lines -- Phase 2's criteria
test-acceptance:
    @{{scripts}}/test.sh acceptance

# Everything CI runs plus everything it can't, cheapest first
test-all:
    @{{scripts}}/test.sh all

# The two rules CI greps for: one Log file, no Android imports in domain/
structure:
    @{{scripts}}/test.sh structure

# Static check: wiki/ and AGENTS.md claims vs the repo
wiki-lint:
    @{{scripts}}/test.sh wiki

# Android Lint, debug and release -- release needs no signing config
lint:
    @{{scripts}}/test.sh lint

# Debug APK
build:
    @{{scripts}}/test.sh build

# Boot the emulator and leave it running, for repeated device runs
emulator:
    # Deliberately not scripts/test.sh: that one tears down what it starts,
    # which is the opposite of what you want when iterating.
    emulator -avd ${AVD:-dev} -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
