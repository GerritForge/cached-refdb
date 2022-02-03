load("//tools/bzl:junit.bzl", "junit_tests")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_DEPS_NEVERLINK",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "cached-refdb",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: cached-refdb",
        "Implementation-Title: cached-refdb plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/admin/repos/modules/cached-refdb",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [],
)

junit_tests(
    name = "cached-refdb_tests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "local",
        "cached-refdb",
    ],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":cached-refdb__plugin",
    ],
)

acceptance_tests(
    srcs = glob(["src/test/java/**/*IT.java"]),
    group = "jgit_cache",
    labels = ["server"],
    vm_args = ["-Xmx2G"],
    deps = [
        ":cached-refdb__plugin",
    ],
)
