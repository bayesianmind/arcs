licenses(["notice"])

package(default_visibility = [
    "//javaharness/java/arcs:__subpackages__",
    "//javaharness/javatests/arcs:__subpackages__",
])

load("//tools/build_defs/android:rules.bzl", "android_library")

android_library(
    name = "api-android",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    javacopts = ["-Xep:AndroidJdkLibsChecker:OFF"],
    deps = [
        "//java/arcs/core/common",
        "//java/arcs/core/crdt",
        "//java/arcs/core/crdt/internal",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
    ],
)
