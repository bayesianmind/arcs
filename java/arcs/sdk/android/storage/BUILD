load("//tools/build_defs/kotlin:rules.bzl", "kt_android_library")

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

kt_android_library(
    name = "storage",
    srcs = glob(["*.kt"]),
    manifest = "//java/arcs/android/common:AndroidManifest.xml",
    deps = [
        "//java/arcs/android/crdt",
        "//java/arcs/android/storage",
        "//java/arcs/core/crdt",
        "//java/arcs/core/storage",
        "//java/arcs/core/storage/util",
        "//java/arcs/sdk/android/storage/service",
        "//java/arcs/sdk/android/storage/service:aidl",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/lifecycle",
        "//third_party/kotlin/kotlinx_coroutines",
    ],
)
