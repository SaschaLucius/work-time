#!/bin/sh
# Resolve and export JDK 21 for this project.
# Java 26+ currently breaks the Kotlin/Gradle script compiler used here.

jdk21_candidates() {
    if [ -n "${WORKTIME_JAVA_HOME:-}" ]; then
        printf '%s\n' "$WORKTIME_JAVA_HOME"
    fi
    printf '%s\n' \
        "${APP_HOME:-}/.jdks/21" \
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    if [ -d "$HOME/.sdkman/candidates/java" ]; then
        # Prefer explicit 21.* installs, then "current" if it is 21.
        for dir in "$HOME"/.sdkman/candidates/java/21*; do
            [ -d "$dir" ] && printf '%s\n' "$dir"
        done
        if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
            printf '%s\n' "$HOME/.sdkman/candidates/java/current"
        fi
    fi
    if [ -d "/Library/Java/JavaVirtualMachines" ]; then
        for dir in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
            [ -d "$dir" ] && printf '%s\n' "$dir"
        done
    fi
}

jdk21_is_21() {
    home=$1
    [ -x "$home/bin/java" ] || return 1
    "$home/bin/java" -version 2>&1 | grep -E 'version "21\.' >/dev/null
}

jdk21_resolve() {
    # Keep an already-selected JDK 21.
    if [ -n "${JAVA_HOME:-}" ] && jdk21_is_21 "$JAVA_HOME"; then
        printf '%s\n' "$JAVA_HOME"
        return 0
    fi

    candidate=
    for candidate in $(jdk21_candidates); do
        if jdk21_is_21 "$candidate"; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

jdk21_export() {
    home=$(jdk21_resolve) || return 1
    export JAVA_HOME="$home"
    case ":$PATH:" in
        *":$JAVA_HOME/bin:"*) ;;
        *) export PATH="$JAVA_HOME/bin:$PATH" ;;
    esac
    return 0
}
