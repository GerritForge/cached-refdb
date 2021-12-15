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
    name = "gerrit-cached-refdb",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: gerrit-cached-refdb",
        "Implementation-Title: gerrit-cached-refdb plugin",
        "Implementation-URL: TODO",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [],
)

junit_tests(
    name = "gerrit-cached-refdb_tests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "local",
        "gerrit-cached-refdb",
    ],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":gerrit-cached-refdb__plugin",
    ],
)

acceptance_tests(
    srcs = glob(["src/test/java/**/*IT.java"]),
    group = "jgit_cache",
    labels = ["server"],
    vm_args = ["-Xmx2G"],
    deps = [
        ":gerrit-cached-refdb__plugin",
    ],
)