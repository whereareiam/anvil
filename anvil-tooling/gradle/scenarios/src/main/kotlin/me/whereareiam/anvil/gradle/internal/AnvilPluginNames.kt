package me.whereareiam.anvil.gradle.internal

internal object AnvilPluginNames {
    const val EXTENSION = "anvil"
    const val STATE_EXTENSION = "anvilPluginState"
    const val UNIT_REGISTRY_EXTENSION = "anvilUnits"
    const val SOURCE_SET = "anvil"
    const val PROTOCOL_RUNTIME_CONFIGURATION = "anvilProtocolRuntime"
    const val TASK_GROUP = "anvil"
    const val LOGIN_TASK = "anvilLogin"
    const val LOGOUT_TASK = "anvilLogout"
    const val SCENARIO_TASK = "anvilScenario"
    const val VERSION_PROPERTY = "anvilVersion"
    const val SCENARIO_OPTION = "scenario"
    const val LIST_OPTION = "list"
    const val GROUP_OPTION = "group"
    const val PROVIDER_OPTION = "provider"
    const val PROFILE_OPTION = "auth-profile"
    const val LOGIN_OPERATION = "login"
    const val LOGOUT_OPERATION = "logout"
    const val VERSION_RESOURCE = "/META-INF/anvil/plugin.properties"
    const val ARTIFACT_NAME_PATTERN = "[A-Za-z0-9_.-]+"
}
