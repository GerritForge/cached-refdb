load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)

gerrit_plugin(
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: cached-refdb",
        "Implementation-Title: cached-refdb plugin",
        "Implementation-URL: https://github.com/GerritForge/cached-refdb",
    ],
    plugin = "cached-refdb",
    deps = ["//plugins/gerrit-bsl-license"],
)

gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*Test.java"]),
    plugin = "cached-refdb",
    tags = ["local"],
)

gerrit_plugin_tests(
    name = "cached-refdb_acceptance_tests",
    srcs = glob(["src/test/java/**/*IT.java"]),
    jvm_flags = ["-Xmx2G"],
    plugin = "cached-refdb",
)
