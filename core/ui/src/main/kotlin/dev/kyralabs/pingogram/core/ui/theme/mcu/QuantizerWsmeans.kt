/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported to Kotlin by the Pingogram authors from material-color-utilities 0.4.0
 * (https://github.com/material-foundation/material-color-utilities): quantize/quantizer_wsmeans and
 * quantize/lab_point_provider. Restructured into smaller functions; the arithmetic and its order are upstream's.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import java.util.Random
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Weighted k-means in L*a*b*, as upstream's QuantizerWsmeans: each distinct color is a point weighted by its count,
 * starting from the given clusters (Celebi gives Wu's colors). Upstream's JavaScript leaves each row of cluster
 * distances in place (its sort has no comparator), so a point is compared with cluster j only when cluster j is closer
 * to its own than four times its distance; this port does the same. Its random starting assignment comes from Java's
 * `Random(0x42688)`, as upstream's Java does, so a result can be reproduced.
 */
internal object QuantizerWsmeans {
    private const val MAX_ITERATIONS = 10
    private const val MIN_MOVEMENT_DISTANCE = 3.0
    private const val RANDOM_SEED = 0x42688L

    /** The clusters of [inputPixels] (ARGB) and how many pixels each holds, at most [maxColors] of them. */
    fun quantize(
        inputPixels: IntArray,
        startingClusters: List<Int>,
        maxColors: Int,
    ): Map<Int, Int> {
        val pixelToCount = LinkedHashMap<Int, Int>()
        for (pixel in inputPixels) pixelToCount[pixel] = (pixelToCount[pixel] ?: 0) + 1
        val points = pixelToCount.keys.map(::labFromArgb)
        val counts = pixelToCount.values.toIntArray()
        var clusterCount = min(maxColors, points.size)
        if (startingClusters.isNotEmpty()) clusterCount = min(clusterCount, startingClusters.size)
        val random = Random(RANDOM_SEED)
        val clusters = startingClusters.map(::labFromArgb).toMutableList()
        val additionalClustersNeeded = clusterCount - clusters.size
        if (startingClusters.isEmpty() && additionalClustersNeeded > 0) {
            repeat(additionalClustersNeeded) { clusters += randomLab(random) }
        }
        val state = Clustering(points, counts, clusters, IntArray(points.size) { random.nextInt(clusterCount) })
        for (iteration in 0 until MAX_ITERATIONS) {
            val pointsMoved = state.reassign(clusterDistances(clusters, clusterCount), clusterCount)
            if (pointsMoved == 0 && iteration != 0) break
            state.recenter(clusterCount)
        }
        return state.populations(clusterCount)
    }

    /** A random L*a*b* point, as upstream draws one where no cluster is given. */
    private fun randomLab(random: Random): DoubleArray =
        doubleArrayOf(
            random.nextDouble() * 100.0,
            random.nextDouble() * (100.0 - (-100.0) + 1) + -100,
            random.nextDouble() * (100.0 - (-100.0) + 1) + -100,
        )

    /** The squared distance between each two clusters; -1 from a cluster to itself, as upstream leaves it. */
    private fun clusterDistances(
        clusters: List<DoubleArray>,
        clusterCount: Int,
    ): Array<DoubleArray> =
        Array(clusterCount) { i ->
            DoubleArray(clusterCount) { j -> if (i == j) -1.0 else distance(clusters[i], clusters[j]) }
        }

    /** The points, their counts and clusters, and which cluster each point is in. */
    private class Clustering(
        val points: List<DoubleArray>,
        val counts: IntArray,
        val clusters: MutableList<DoubleArray>,
        val clusterIndices: IntArray,
    ) {
        var pixelCountSums: IntArray = IntArray(0)

        /** Moves each point to a nearer cluster where that is worth it; how many moved. */
        fun reassign(
            distances: Array<DoubleArray>,
            clusterCount: Int,
        ): Int {
            var pointsMoved = 0
            for (i in points.indices) {
                val previousClusterIndex = clusterIndices[i]
                val previousDistance = distance(points[i], clusters[previousClusterIndex])
                var minimumDistance = previousDistance
                var newClusterIndex = -1
                for (j in 0 until clusterCount) {
                    val distance =
                        if (distances[previousClusterIndex][j] < 4 * previousDistance) distance(points[i], clusters[j]) else null
                    if (distance != null && distance < minimumDistance) {
                        minimumDistance = distance
                        newClusterIndex = j
                    }
                }
                val distanceChange = abs(sqrt(minimumDistance) - sqrt(previousDistance))
                if (newClusterIndex != -1 && distanceChange > MIN_MOVEMENT_DISTANCE) {
                    pointsMoved++
                    clusterIndices[i] = newClusterIndex
                }
            }
            return pointsMoved
        }

        /** Moves each cluster to the weighted mean of its points; an empty one to (0, 0, 0). */
        fun recenter(clusterCount: Int) {
            val sums = Array(clusterCount) { DoubleArray(3) }
            pixelCountSums = IntArray(clusterCount)
            for (i in points.indices) {
                val clusterIndex = clusterIndices[i]
                val point = points[i]
                val count = counts[i]
                pixelCountSums[clusterIndex] += count
                for (component in 0 until 3) sums[clusterIndex][component] += point[component] * count
            }
            for (i in 0 until clusterCount) {
                val count = pixelCountSums[i]
                clusters[i] = if (count == 0) DoubleArray(3) else DoubleArray(3) { sums[i][it] / count }
            }
        }

        /** Each non-empty cluster's color and population, the first of equal colors kept. */
        fun populations(clusterCount: Int): Map<Int, Int> {
            val argbToPopulation = LinkedHashMap<Int, Int>()
            for (i in 0 until clusterCount) {
                val count = pixelCountSums.getOrElse(i) { 0 }
                val argb = argbFromLab(clusters[i][0], clusters[i][1], clusters[i][2])
                if (count != 0 && argb !in argbToPopulation) argbToPopulation[argb] = count
            }
            return argbToPopulation
        }
    }

    /** Squared Euclidean distance in L*a*b*, as upstream's LabPointProvider. */
    private fun distance(
        from: DoubleArray,
        to: DoubleArray,
    ): Double {
        val dL = from[0] - to[0]
        val dA = from[1] - to[1]
        val dB = from[2] - to[2]
        return dL * dL + dA * dA + dB * dB
    }
}
