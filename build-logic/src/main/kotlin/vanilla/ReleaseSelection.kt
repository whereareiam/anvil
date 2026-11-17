package me.whereareiam.anvil.buildlogic.vanilla

import org.gradle.api.GradleException

/**
 * The releases `generateVanillaData` runs: every Paper row of the live matrix, or the subset `-Pminecraft=` names,
 * comma-separated, such as `-Pminecraft=1.21.11,26.3`.
 */
object ReleaseSelection {
	/**
	 * The Gradle property that names a subset.
	 */
	const val PROPERTY = "minecraft"

	/**
	 * Selects from [matrix], the Paper rows' Minecraft versions and their Java in the matrix's order, what [requested]
	 * names, keeping the matrix's order; every row when [requested] is `null`.
	 *
	 * @throws GradleException when [requested] names nothing or a version the matrix does not run
	 */
	fun select(matrix: Map<String, Int>, requested: String?): Map<String, Int> {
		if (requested == null) return matrix
		val names = requested.split(',').map(String::trim).filter(String::isNotEmpty)
		if (names.isEmpty()) throw GradleException("-P$PROPERTY names no Minecraft version")
		val unknown = names.filterNot(matrix::containsKey)
		if (unknown.isNotEmpty())
			throw GradleException("-P$PROPERTY names $unknown, which live-matrix.toml runs no Paper server of; it runs ${matrix.keys}")
		return matrix.filterKeys(names::contains)
	}
}
